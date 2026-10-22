import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../../db/client.js';
import { verifyAppKey } from '../../../core/middleware/verifyAppKey.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';
import { generateStandaloneChatReply } from '../services/aiAdvisory.js';

const router = Router();

const MAX_MESSAGE_LENGTH = 4000;
const MAX_CONVERSATION_ID_LENGTH = 128;

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

/**
 * GET /api/v1/pashu-health/ai-chats
 * Returns the farmer's standalone AI conversations, newest first.
 */
router.get('/', verifyAppKey, verifySession, requireRole('farmer'), async (req, res) => {
  try {
    const result = await db.execute({
      sql: `SELECT
              m.conversation_id,
              MAX(m.created_at) AS updated_at,
              COUNT(*) AS message_count,
              (
                SELECT t.content
                FROM pashu_ai_chat_messages t
                WHERE t.conversation_id = m.conversation_id
                  AND t.farmer_id = m.farmer_id
                  AND t.role = 'user'
                ORDER BY t.created_at ASC
                LIMIT 1
              ) AS title
            FROM pashu_ai_chat_messages m
            WHERE m.farmer_id = ?
            GROUP BY m.conversation_id
            ORDER BY updated_at DESC`,
      args: [req.userId],
    });

    const conversations = result.rows.map((row) => ({
      id: row.conversation_id,
      title: titleFromMessage(row.title),
      updatedAt: Number(row.updated_at ?? 0),
      messageCount: Number(row.message_count ?? 0),
    }));

    return res.status(200).json({ conversations });
  } catch (error) {
    console.error('[pashu-health/ai-chats] list failed:', error);
    return res.status(500).json({
      error: 'chat_list_failed',
      message: 'Could not load chat history.',
    });
  }
});

/**
 * GET /api/v1/pashu-health/ai-chats/:id
 * Returns one farmer-owned conversation and all its messages.
 */
router.get('/:id', verifyAppKey, verifySession, requireRole('farmer'), async (req, res) => {
  const conversationId = req.params.id;
  try {
    const owner = await db.execute({
      sql: `SELECT id FROM pashu_ai_chat_messages
            WHERE conversation_id = ? AND farmer_id = ?
            LIMIT 1`,
      args: [conversationId, req.userId],
    });

    if (owner.rows.length === 0) {
      return res.status(404).json({ error: 'not_found', message: 'Conversation not found.' });
    }

    const result = await db.execute({
      sql: `SELECT id, role, content, created_at
            FROM pashu_ai_chat_messages
            WHERE conversation_id = ? AND farmer_id = ?
            ORDER BY created_at ASC`,
      args: [conversationId, req.userId],
    });

    return res.status(200).json({
      conversationId,
      messages: result.rows.map((row) => ({
        id: row.id,
        role: row.role,
        content: row.content,
        createdAt: Number(row.created_at ?? 0),
      })),
    });
  } catch (error) {
    console.error('[pashu-health/ai-chats/:id] lookup failed:', error);
    return res.status(500).json({
      error: 'chat_lookup_failed',
      message: 'Could not load the conversation.',
    });
  }
});

/**
 * POST /api/v1/pashu-health/ai-chats
 * Body: { message, conversationId? }
 * Starts a new conversation when conversationId is omitted.
 */
router.post('/', verifyAppKey, verifySession, requireRole('farmer'), async (req, res) => {
  const message = str(req.body?.message);
  const requestedConversationId = str(req.body?.conversationId);

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

  try {
    const userResult = await db.execute({
      sql: 'SELECT preferred_language FROM users WHERE id = ?',
      args: [req.userId],
    });

    if (userResult.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    const preferredLanguage = userResult.rows[0].preferred_language ?? 'hi';
    const history = [];

    if (requestedConversationId) {
      const owner = await db.execute({
        sql: `SELECT id FROM pashu_ai_chat_messages
              WHERE conversation_id = ? AND farmer_id = ?
              LIMIT 1`,
        args: [requestedConversationId, req.userId],
      });

      if (owner.rows.length === 0) {
        return res.status(404).json({ error: 'not_found', message: 'Conversation not found.' });
      }

      const historyResult = await db.execute({
        sql: `SELECT role, content
              FROM pashu_ai_chat_messages
              WHERE conversation_id = ? AND farmer_id = ?
              ORDER BY created_at ASC`,
        args: [requestedConversationId, req.userId],
      });

      history.push(...historyResult.rows.map((row) => ({
        role: row.role,
        content: row.content,
      })));
    }

    const conversationId = requestedConversationId ?? randomUUID();
    history.push({ role: 'user', content: message });

    const reply = await generateStandaloneChatReply(history, preferredLanguage);
    const now = Date.now();

    await db.batch(
      [
        {
          sql: `INSERT INTO pashu_ai_chat_messages
                (id, conversation_id, farmer_id, role, content, created_at)
                VALUES (?, ?, ?, ?, ?, ?)`,
          args: [randomUUID(), conversationId, req.userId, 'user', message, now],
        },
        {
          sql: `INSERT INTO pashu_ai_chat_messages
                (id, conversation_id, farmer_id, role, content, created_at)
                VALUES (?, ?, ?, ?, ?, ?)`,
          args: [randomUUID(), conversationId, req.userId, 'assistant', reply, now + 1],
        },
      ],
      'write',
    );

    return res.status(200).json({
      conversationId,
      reply,
      title: titleFromMessage(history.find((item) => item.role === 'user')?.content),
    });
  } catch (error) {
    console.error('[pashu-health/ai-chats] send failed:', error);
    return res.status(500).json({
      error: 'chat_failed',
      message: 'Could not process the chat message.',
    });
  }
});

export default router;
