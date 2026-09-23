import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../db/client.js';
import { adminSecret } from '../middleware/adminSecret.js';

/**
 * District magistrate seeding — admin-only (X-Admin-Secret).
 * Magistrates log in on the government web with email + OTP; their account
 * (and district) must exist here first.
 */

const router = Router();

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value);
  return text === '' ? null : text;
}

function normalizeEmail(email) {
  return String(email ?? '').trim().toLowerCase();
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

/** POST /api/v1/core/admin/magistrates  Body: { email, name, district, state? } */
router.post('/admin/magistrates', adminSecret, async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const name = str(req.body?.name);
  const district = str(req.body?.district);
  const state = str(req.body?.state);

  if (!isValidEmail(email)) {
    return res.status(400).json({ error: 'invalid_email', message: 'A valid email address is required.' });
  }
  if (!name || !district) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Body must include non-empty "name" and "district" strings.',
    });
  }

  try {
    const existing = await db.execute({ sql: 'SELECT id FROM gov_magistrates WHERE email = ?', args: [email] });
    if (existing.rows.length > 0) {
      return res.status(409).json({ error: 'email_taken', message: 'A magistrate with this email already exists.' });
    }

    const id = randomUUID();
    const createdAt = Date.now();
    await db.execute({
      sql: 'INSERT INTO gov_magistrates (id, email, name, district, state, created_at) VALUES (?, ?, ?, ?, ?, ?)',
      args: [id, email, name, district, state, createdAt],
    });

    return res.status(201).json({ magistrate: { id, email, name, district, state: state ?? null, createdAt } });
  } catch (error) {
    console.error('[admin/magistrates] create failed:', error);
    return res.status(500).json({ error: 'create_failed', message: 'Could not create the magistrate.' });
  }
});

/** GET /api/v1/core/admin/magistrates */
router.get('/admin/magistrates', adminSecret, async (_req, res) => {
  try {
    const result = await db.execute('SELECT * FROM gov_magistrates ORDER BY district ASC, name ASC');
    return res.status(200).json({ magistrates: result.rows });
  } catch (error) {
    console.error('[admin/magistrates] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list magistrates.' });
  }
});

/** DELETE /api/v1/core/admin/magistrates/:id */
router.delete('/admin/magistrates/:id', adminSecret, async (req, res) => {
  try {
    const result = await db.execute({ sql: 'DELETE FROM gov_magistrates WHERE id = ?', args: [req.params.id] });
    if (result.rowsAffected === 0) {
      return res.status(404).json({ error: 'not_found', message: 'Magistrate not found.' });
    }
    return res.status(200).json({ success: true });
  } catch (error) {
    console.error('[admin/magistrates] delete failed:', error);
    return res.status(500).json({ error: 'delete_failed', message: 'Could not delete the magistrate.' });
  }
});

export default router;
