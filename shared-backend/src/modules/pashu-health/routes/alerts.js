import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../../db/client.js';

const router = Router();

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value);
  return text === '' ? null : text;
}

function num(value, fallback = null) {
  if (value === undefined || value === null || value === '') return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function toDbRead(value) {
  return value === true || value === 1 || value === '1' || value === 'true' ? 1 : 0;
}

/** POST /api/v1/pashu-health/alerts — create or upsert by id. */
router.post('/', async (req, res) => {
  const body = req.body ?? {};
  const id = str(body.id) ?? randomUUID();
  const recipientRole = str(body.recipientRole) ?? str(body.recipient_role);
  const recipientId = str(body.recipientId) ?? str(body.recipient_id);
  const message = str(body.message);
  const read = toDbRead(body.read);
  const createdAt = num(body.createdAt ?? body.created_at, Date.now());

  if (!recipientRole || !recipientId || !message) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Fields "recipientRole", "recipientId", and "message" are required.',
    });
  }

  try {
    await db.execute({
      sql: `INSERT INTO pashu_alerts (id, recipient_role, recipient_id, message, read, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          recipient_role = excluded.recipient_role,
          recipient_id = excluded.recipient_id,
          message = excluded.message,
          read = excluded.read,
          created_at = excluded.created_at`,
      args: [id, recipientRole, recipientId, message, read, createdAt],
    });
    const created = await db.execute({
      sql: 'SELECT * FROM pashu_alerts WHERE id = ?',
      args: [id],
    });
    return res.status(200).json({ success: true, alert: created.rows[0] });
  } catch (error) {
    console.error('[pashu-health/alerts] save failed:', error);
    return res.status(500).json({ error: 'save_failed', message: 'Could not save the alert.' });
  }
});

/** GET /api/v1/pashu-health/alerts */
router.get('/', async (_req, res) => {
  try {
    const result = await db.execute('SELECT * FROM pashu_alerts ORDER BY created_at DESC');
    return res.status(200).json({ alerts: result.rows });
  } catch (error) {
    console.error('[pashu-health/alerts] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list alerts.' });
  }
});

/** GET /api/v1/pashu-health/alerts/:id */
router.get('/:id', async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'SELECT * FROM pashu_alerts WHERE id = ?',
      args: [req.params.id],
    });
    const row = result.rows[0];
    if (!row) {
      return res.status(404).json({ error: 'not_found', message: 'Alert not found.' });
    }
    return res.status(200).json({ alert: row });
  } catch (error) {
    console.error('[pashu-health/alerts] get failed:', error);
    return res.status(500).json({ error: 'get_failed', message: 'Could not load the alert.' });
  }
});

/** PUT /api/v1/pashu-health/alerts/:id — partial update (e.g. mark read). */
router.put('/:id', async (req, res) => {
  const body = req.body ?? {};
  const id = req.params.id;

  try {
    const existing = await db.execute({
      sql: 'SELECT * FROM pashu_alerts WHERE id = ?',
      args: [id],
    });
    const row = existing.rows[0];
    if (!row) {
      return res.status(404).json({ error: 'not_found', message: 'Alert not found.' });
    }

    const recipientRole = str(body.recipientRole) ?? str(body.recipient_role) ?? row.recipient_role;
    const recipientId = str(body.recipientId) ?? str(body.recipient_id) ?? row.recipient_id;
    const message = str(body.message) ?? row.message;
    const read = body.read === undefined ? row.read : toDbRead(body.read);
    const createdAt = num(body.createdAt ?? body.created_at, row.created_at);

    await db.execute({
      sql: `UPDATE pashu_alerts SET
          recipient_role = ?, recipient_id = ?, message = ?, read = ?, created_at = ?
        WHERE id = ?`,
      args: [recipientRole, recipientId, message, read, createdAt, id],
    });
    const updated = await db.execute({
      sql: 'SELECT * FROM pashu_alerts WHERE id = ?',
      args: [id],
    });
    return res.status(200).json({ success: true, alert: updated.rows[0] });
  } catch (error) {
    console.error('[pashu-health/alerts] update failed:', error);
    return res.status(500).json({ error: 'update_failed', message: 'Could not update the alert.' });
  }
});

/** DELETE /api/v1/pashu-health/alerts/:id */
router.delete('/:id', async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'DELETE FROM pashu_alerts WHERE id = ?',
      args: [req.params.id],
    });
    if (result.rowsAffected === 0) {
      return res.status(404).json({ error: 'not_found', message: 'Alert not found.' });
    }
    return res.status(200).json({ success: true });
  } catch (error) {
    console.error('[pashu-health/alerts] delete failed:', error);
    return res.status(500).json({ error: 'delete_failed', message: 'Could not delete the alert.' });
  }
});

export default router;
