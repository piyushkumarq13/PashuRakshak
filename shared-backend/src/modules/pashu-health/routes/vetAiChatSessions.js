import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../../db/client.js';
import { verifyAppKey } from '../../../core/middleware/verifyAppKey.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';
import { formatVetCaseContext, generateVetChatReply } from '../services/aiAdvisory.js';

const router = Router();

const MAX_MESSAGE_LENGTH = 4000;
const MAX_CONVERSATION_ID_LENGTH = 128;
const MAX_REPORT_ID_LENGTH = 128;

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value).trim();
  return text === '' ? null : text;
}

function titleFromMessage(message) {
  const firstLine = (message ?? '').split(/\r?\n/)[0].replace(/\s+/g, ' ').trim();
  if (!firstLine) return 'New conversation';
  return firstLine.length > 80 ? `${firstLine.slice(0, 77)}...` : firstLine;
}

function caseLabel(row) {
  if (!row) return null;
  const animalName = str(row.animal_name);
  const species = str(row.animal_species);
  if (animalName) return species ? `${animalName} (${species})` : animalName;
  const id = str(row.id);
  return id ? `Case ${id.slice(0, 8)}` : null;
}

async function loadReport(reportId) {
  const result = await db.execute({
    sql: `SELECT
            p.id,
            p.animal_id,
            p.farmer_id,
            p.symptoms,
            p.risk_score,
            p.risk_breakdown,
            p.status,
            p.created_at,
            p.village,
            p.ai_risk_category,
            p.disease_description,
            p.vet_assessment,
            p.resolution_note,
            p.resolved_at,
            p.qr_verified,
            p.latitude,
            p.longitude,
            a.name AS animal_name,
            a.species AS animal_species
          FROM pashu_symptom_reports p
          LEFT JOIN pashu_animals a ON a.id = p.animal_id
          WHERE p.id = ?
          LIMIT 1`,
    args: [reportId],
  });
  return result.rows[0] ?? null;
}

async function loadCaseLabels(reportIds) {
  const ids = [...new Set(reportIds.filter(Boolean))];
  const labels = new Map();
  if (ids.length === 0) return labels;

  const placeholders = ids.map(() => '?').join(', ');
  const result = await db.execute({
    sql: `SELECT
            p.id,
            a.name AS animal_name,
            a.species AS animal_species
          FROM pashu_symptom_reports p
          LEFT JOIN pashu_animals a ON a.id = p.animal_id
          WHERE p.id IN (${placeholders})`,
    args: ids,
  });

  result.rows.forEach((row) => {
    labels.set(row.id, caseLabel(row));
  });
  ids.forEach((id) => {
    if (!labels.has(id)) labels.set(id, `Case ${id.slice(0, 8)}`);
  });
  return labels;
}

/**
 * GET /api/v1/pashu-health/vet-ai-chats
 * Lists the signed-in vet's conversations, newest first. Optional `reportId`
 * query filters to one case.
 */
router.get('/', verifyAppKey, verifySession, requireRole('vet'), async (req, res) => {
  const reportIdFilter = str(req.query?.reportId);
  try {
    const result = await db.execute({
      sql: `SELECT
              m.conversation_id,
              MAX(m.created_at) AS updated_at,
              COUNT(*) AS message_count,
              MAX(m.report_id) AS report_id,
              (
                SELECT t.content
                FROM pashu_ai_vet_chat_messages t
                WHERE t.conversation_id = m.conversation_id
                  AND t.vet_id = m.vet_id
                  AND t.role = 'user'
                ORDER BY t.created_at ASC
                LIMIT 1
              ) AS title
            FROM pashu_ai_vet_chat_messages m
            WHERE m.vet_id = ?
              ${reportIdFilter ? 'AND m.report_id = ?' : ''}
            GROUP BY m.conversation_id
            ORDER BY updated_at DESC`,
      args: reportIdFilter ? [req.userId, reportIdFilter] : [req.userId],
    });

    const labels = await loadCaseLabels(result.rows.map((row) => row.report_id));
    const conversations = result.rows.map((row) => {
      const reportId = str(row.report_id);
      return {
        id: row.conversation_id,
        title: titleFromMessage(row.title),
        updatedAt: Number(row.updated_at ?? 0),
        messageCount: Number(row.message_count ?? 0),
        reportId,
        reportLabel: reportId ? labels.get(reportId) ?? `Case ${reportId.slice(0, 8)}` : null,
      };
    });

    return res.status(200).json({ conversations });
  } catch (error) {
    console.error('[pashu-health/vet-ai-chats] list failed:', error);
    return res.status(500).json({
      error: 'chat_list_failed',
      message: 'Could not load chat history.',
    });
  }
});

/**
 * GET /api/v1/pashu-health/vet-ai-chats/:id
 * Returns one vet-owned conversation and all its messages.
 */
router.get('/:id', verifyAppKey, verifySession, requireRole('vet'), async (req, res) => {
  const conversationId = req.params.id;
  try {
    const owner = await db.execute({
      sql: `SELECT report_id FROM pashu_ai_vet_chat_messages
            WHERE conversation_id = ? AND vet_id = ?
            LIMIT 1`,
      args: [conversationId, req.userId],
    });

    if (owner.rows.length === 0) {
      return res.status(404).json({ error: 'not_found', message: 'Conversation not found.' });
    }

    const result = await db.execute({
      sql: `SELECT id, role, content, created_at
            FROM pashu_ai_vet_chat_messages
            WHERE conversation_id = ? AND vet_id = ?
            ORDER BY created_at ASC`,
      args: [conversationId, req.userId],
    });

    const reportId = str(owner.rows[0].report_id);
    const labels = await loadCaseLabels(reportId ? [reportId] : []);

    return res.status(200).json({
      conversationId,
      reportId,
      reportLabel: reportId ? labels.get(reportId) ?? `Case ${reportId.slice(0, 8)}` : null,
      messages: result.rows.map((row) => ({
        id: row.id,
        role: row.role,
        content: row.content,
        createdAt: Number(row.created_at ?? 0),
      })),
    });
  } catch (error) {
    console.error('[pashu-health/vet-ai-chats/:id] lookup failed:', error);
    return res.status(500).json({
      error: 'chat_lookup_failed',
      message: 'Could not load the conversation.',
    });
  }
});

/**
 * POST /api/v1/pashu-health/vet-ai-chats
 * Body: { message, conversationId?, reportId? }
 * Starts a new conversation when conversationId is omitted. Supplying reportId
 * scopes the conversation to that case and injects its briefing into the AI.
 */
router.post('/', verifyAppKey, verifySession, requireRole('vet'), async (req, res) => {
  const message = str(req.body?.message);
  const requestedConversationId = str(req.body?.conversationId);
  const requestedReportId = str(req.body?.reportId);

  if (!message) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Body must include a non-empty "message" string.',
    });
  }

  if (message.length > MAX_MESSAGE_LENGTH) {
    return res.status(400).json({
      error: 'message_too_long',
      message: `Message must be at most ${MAX_MESSAGE_LENGTH} characters.`,
    });
  }

  if (requestedConversationId && requestedConversationId.length > MAX_CONVERSATION_ID_LENGTH) {
    return res.status(400).json({ error: 'invalid_conversation_id' });
  }

  if (requestedReportId && requestedReportId.length > MAX_REPORT_ID_LENGTH) {
    return res.status(400).json({ error: 'invalid_report_id' });
  }

  try {
    const userResult = await db.execute({
      sql: 'SELECT preferred_language FROM users WHERE id = ?',
      args: [req.userId],
    });

    if (userResult.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    const preferredLanguage = userResult.rows[0].preferred_language ?? 'en';
    const history = [];
    let reportId = requestedReportId;

    if (requestedConversationId) {
      const owner = await db.execute({
        sql: `SELECT report_id FROM pashu_ai_vet_chat_messages
              WHERE conversation_id = ? AND vet_id = ?
              LIMIT 1`,
        args: [requestedConversationId, req.userId],
      });

      if (owner.rows.length === 0) {
        return res.status(404).json({ error: 'not_found', message: 'Conversation not found.' });
      }

      const existingReportId = str(owner.rows[0].report_id);
      if (requestedReportId && requestedReportId !== existingReportId) {
        return res.status(400).json({
          error: 'report_mismatch',
          message: 'The conversation belongs to a different case.',
        });
      }
      reportId = existingReportId;

      const historyResult = await db.execute({
        sql: `SELECT role, content
              FROM pashu_ai_vet_chat_messages
              WHERE conversation_id = ? AND vet_id = ?
              ORDER BY created_at ASC`,
        args: [requestedConversationId, req.userId],
      });

      history.push(...historyResult.rows.map((row) => ({
        role: row.role,
        content: row.content,
      })));
    }

    let report = null;
    if (reportId) {
      report = await loadReport(reportId);
      if (!report) {
        return res.status(404).json({ error: 'not_found', message: 'Case not found.' });
      }
    }

    const conversationId = requestedConversationId ?? randomUUID();
    history.push({ role: 'user', content: message });

    const reply = await generateVetChatReply(history, {
      preferredLanguage,
      reportContext: formatVetCaseContext(report),
    });
    const now = Date.now();

    await db.batch(
      [
        {
          sql: `INSERT INTO pashu_ai_vet_chat_messages
                (id, conversation_id, vet_id, report_id, role, content, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)`,
          args: [randomUUID(), conversationId, req.userId, reportId, 'user', message, now],
        },
        {
          sql: `INSERT INTO pashu_ai_vet_chat_messages
                (id, conversation_id, vet_id, report_id, role, content, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)`,
          args: [randomUUID(), conversationId, req.userId, reportId, 'assistant', reply, now + 1],
        },
      ],
      'write',
    );

    return res.status(200).json({
      conversationId,
      reply,
      title: titleFromMessage(history.find((item) => item.role === 'user')?.content),
      reportId,
      reportLabel: caseLabel(report),
    });
  } catch (error) {
    console.error('[pashu-health/vet-ai-chats] send failed:', error);
    return res.status(500).json({
      error: 'chat_failed',
      message: 'Could not process the chat message.',
    });
  }
});

export default router;
