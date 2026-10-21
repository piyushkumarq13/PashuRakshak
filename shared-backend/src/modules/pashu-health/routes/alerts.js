import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../../db/client.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';
import { appendAuditLog } from '../services/auditLog.js';

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

const ALL_VETS = 'all-vets';

/** True when this session is an intended recipient of the alert. */
function canAccessAlert(req, recipientRole, recipientId) {
  if (recipientRole !== req.role) return false;
  if (recipientId === req.userId) return true;
  return req.role === 'vet' && recipientId === ALL_VETS;
}

async function userRoleExists(role, id) {
  if (role === 'vet' && id === ALL_VETS) return true;
  if (!id || (role !== 'farmer' && role !== 'vet')) return false;
  const result = await db.execute({
    sql: 'SELECT id FROM users WHERE id = ? AND role = ?',
    args: [id, role],
  });
  return result.rows.length > 0;
}

/** POST /api/v1/pashu-health/alerts — create or upsert by id. */
router.post('/', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
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
  if (recipientRole !== 'farmer' && recipientRole !== 'vet') {
    return res.status(400).json({
      error: 'invalid_recipient_role',
      message: '"recipientRole" must be "farmer" or "vet".',
    });
  }

  try {
    if (req.role === 'farmer') {
      if (recipientRole === 'farmer' && recipientId !== req.userId) {
        return res.status(403).json({
          error: 'forbidden_alert',
          message: 'A farmer may only create alerts for themselves.',
        });
      }
      if (recipientRole === 'vet' && !(await userRoleExists('vet', recipientId))) {
        return res.status(403).json({
          error: 'invalid_recipient',
          message: 'The selected vet does not exist.',
        });
      }
    } else if (recipientRole === 'vet') {
      if (recipientId !== req.userId && recipientId !== ALL_VETS) {
        return res.status(403).json({
          error: 'forbidden_alert',
          message: 'A vet may only create alerts for themselves.',
        });
      }
    } else if (!(await userRoleExists('farmer', recipientId))) {
      return res.status(403).json({
        error: 'invalid_recipient',
        message: 'The selected farmer does not exist.',
      });
    }

    const existing = await db.execute({
      sql: 'SELECT recipient_role, recipient_id FROM pashu_alerts WHERE id = ?',
      args: [id],
    });
    if (
      existing.rows.length > 0 &&
      req.role === 'farmer' &&
      existing.rows[0].recipient_role === 'farmer' &&
      existing.rows[0].recipient_id !== req.userId
    ) {
      return res.status(403).json({
        error: 'forbidden_alert',
        message: 'This alert belongs to another farmer.',
      });
    }

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

/** GET /api/v1/pashu-health/alerts — only alerts addressed to this session. */
router.get('/', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  try {
    const result = req.role === 'vet'
      ? await db.execute({
        sql: `SELECT * FROM pashu_alerts
              WHERE recipient_role = 'vet' AND (recipient_id = ? OR recipient_id = ?)
              ORDER BY created_at DESC`,
        args: [req.userId, ALL_VETS],
      })
      : await db.execute({
        sql: `SELECT * FROM pashu_alerts
              WHERE recipient_role = 'farmer' AND recipient_id = ?
              ORDER BY created_at DESC`,
        args: [req.userId],
      });
    return res.status(200).json({ alerts: result.rows });
  } catch (error) {
    console.error('[pashu-health/alerts] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list alerts.' });
  }
});

/** GET /api/v1/pashu-health/alerts/:id */
router.get('/:id', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'SELECT * FROM pashu_alerts WHERE id = ?',
      args: [req.params.id],
    });
    const row = result.rows[0];
    if (!row || !canAccessAlert(req, row.recipient_role, row.recipient_id)) {
      return res.status(404).json({ error: 'not_found', message: 'Alert not found.' });
    }
    return res.status(200).json({ alert: row });
  } catch (error) {
    console.error('[pashu-health/alerts] get failed:', error);
    return res.status(500).json({ error: 'get_failed', message: 'Could not load the alert.' });
  }
});

/** PUT /api/v1/pashu-health/alerts/:id — recipient marks an alert read. */
router.put('/:id', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  const body = req.body ?? {};
  const id = req.params.id;

  try {
    const existing = await db.execute({
      sql: 'SELECT * FROM pashu_alerts WHERE id = ?',
      args: [id],
    });
    const row = existing.rows[0];
    if (!row || !canAccessAlert(req, row.recipient_role, row.recipient_id)) {
      return res.status(404).json({ error: 'not_found', message: 'Alert not found.' });
    }

    // Recipients may only change the read flag; content/recipient are immutable.
    const read = body.read === undefined ? row.read : toDbRead(body.read);

    await db.execute({
      sql: 'UPDATE pashu_alerts SET read = ? WHERE id = ?',
      args: [read, id],
    });
    const updated = await db.execute({
      sql: 'SELECT * FROM pashu_alerts WHERE id = ?',
      args: [id],
    });
    if (read === 1 && row.read !== 1) {
      await appendAuditLog(db, {
        actor: req.userId,
        action: 'alert_acknowledged',
        entityType: 'alert',
        entityId: id,
        payload: { recipientRole: row.recipient_role, recipientId: row.recipient_id },
      });
    }
    return res.status(200).json({ success: true, alert: updated.rows[0] });
  } catch (error) {
    console.error('[pashu-health/alerts] update failed:', error);
    return res.status(500).json({ error: 'update_failed', message: 'Could not update the alert.' });
  }
});

/** DELETE /api/v1/pashu-health/alerts/:id — only the direct recipient. */
router.delete('/:id', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  try {
    const result = await db.execute({
      sql: `DELETE FROM pashu_alerts
            WHERE id = ? AND recipient_role = ? AND recipient_id = ?`,
      args: [req.params.id, req.role, req.userId],
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
