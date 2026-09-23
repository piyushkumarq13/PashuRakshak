import { Router } from 'express';
import { db } from '../../../db/client.js';
import { verifyAppKey } from '../../../core/middleware/verifyAppKey.js';
import { verifySession } from '../../../core/middleware/verifySession.js';
import { generateAdvisory, continueConversation } from '../services/aiAdvisory.js';

const router = Router();

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value);
  return text === '' ? null : text;
}

/**
 * POST /api/v1/pashu-health/reports/:id/chat
 * Protected by verifyAppKey + verifySession.
 * Accepts { message }. Looks up the farmer's preferred_language,
 * calls continueConversation. Returns { reply }.
 */
router.post('/:id/chat', verifyAppKey, verifySession, async (req, res) => {
  const reportId = req.params.id;
  const userId = req.userId;
  const message = str(req.body?.message);

  if (!message) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Body must include a non-empty "message" string.',
    });
  }

  try {
    const userResult = await db.execute({
      sql: 'SELECT preferred_language FROM users WHERE id = ?',
      args: [userId],
    });

    if (userResult.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    const farmerPreferredLanguage = userResult.rows[0].preferred_language ?? 'hi';

    // Look up the farmer_id for this report
    const reportResult = await db.execute({
      sql: 'SELECT farmer_id FROM pashu_symptom_reports WHERE id = ?',
      args: [reportId],
    });

    if (reportResult.rows.length === 0) {
      return res.status(404).json({ error: 'not_found', message: 'Report not found.' });
    }

    const farmerId = reportResult.rows[0].farmer_id;

    const reply = await continueConversation(reportId, farmerId, message, farmerPreferredLanguage);

    return res.status(200).json({ reply });
  } catch (error) {
    console.error('[pashu-health/reports/:id/chat] failed:', error);
    return res.status(500).json({
      error: 'chat_failed',
      message: 'Could not process the chat message.',
    });
  }
});

/**
 * GET /api/v1/pashu-health/reports/:id/chat
 * Returns the FULL stored conversation history (not the capped version)
 * for display, as { messages: [{ role, content }, ...] }.
 * Returns { messages: [] } when no conversation exists yet.
 */
router.get('/:id/chat', verifySession, async (req, res) => {
  const reportId = req.params.id;

  try {
    const result = await db.execute({
      sql: 'SELECT * FROM pashu_ai_conversations WHERE report_id = ?',
      args: [reportId],
    });

    if (result.rows.length === 0) {
      return res.status(200).json({ messages: [] });
    }

    const history = JSON.parse(result.rows[0].full_history);

    return res.status(200).json({ messages: history });
  } catch (error) {
    console.error('[pashu-health/reports/:id/chat] lookup failed:', error);
    return res.status(500).json({
      error: 'chat_lookup_failed',
      message: 'Could not load the conversation history.',
    });
  }
});

export default router;
