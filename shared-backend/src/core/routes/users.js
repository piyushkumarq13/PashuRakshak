import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../db/client.js';
import { verifyAppKey } from '../middleware/verifyAppKey.js';
import { verifyFirebaseToken } from '../middleware/verifyFirebaseToken.js';

const router = Router();

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value);
  return text === '' ? null : text;
}

/**
 * POST /api/v1/core/users
 * Protected by verifyAppKey + verifyFirebaseToken.
 * Accepts { phone, role, name?, email?, preferredLanguage? }.
 * Upserts into users keyed by phone, also setting firebase_uid from req.uid.
 * Returns the full user row.
 */
router.post('/users', verifyAppKey, verifyFirebaseToken, async (req, res) => {
  const phone = str(req.body?.phone);
  const role = str(req.body?.role);
  const name = str(req.body?.name);
  const email = str(req.body?.email);
  const preferredLanguage = str(req.body?.preferredLanguage);

  if (!phone || !role) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Body must include non-empty "phone" and "role" strings.',
    });
  }

  const uid = req.uid;
  const now = Date.now();

  try {
    const existing = await db.execute({
      sql: 'SELECT id FROM users WHERE phone = ?',
      args: [phone],
    });

    let userId;
    if (existing.rows.length > 0) {
      userId = existing.rows[0].id;
      await db.execute({
        sql: `UPDATE users SET firebase_uid = ?, role = ?, name = ?, email = ?, preferred_language = ?, updated_at = ? WHERE phone = ?`,
        args: [uid, role, name, email, preferredLanguage, now, phone],
      });
    } else {
      userId = randomUUID();
      await db.execute({
        sql: `INSERT INTO users (id, phone, firebase_uid, role, name, email, preferred_language, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        args: [userId, phone, uid, role, name, email, preferredLanguage, now, now],
      });
    }

    const row = await db.execute({
      sql: 'SELECT * FROM users WHERE id = ?',
      args: [userId],
    });

    return res.status(200).json({ user: row.rows[0] });
  } catch (error) {
    console.error('[core/users] upsert failed:', error);
    return res.status(500).json({
      error: 'user_upsert_failed',
      message: 'Could not create or update the user.',
    });
  }
});

/**
 * GET /api/v1/core/users/me
 * Protected by verifyAppKey + verifyFirebaseToken.
 * Looks up users by firebase_uid = req.uid.
 * Returns 404 { exists: false } if not found (signals "needs onboarding").
 */
router.get('/users/me', verifyAppKey, verifyFirebaseToken, async (req, res) => {
  const uid = req.uid;

  try {
    const result = await db.execute({
      sql: 'SELECT * FROM users WHERE firebase_uid = ?',
      args: [uid],
    });

    if (result.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    return res.status(200).json({ user: result.rows[0] });
  } catch (error) {
    console.error('[core/users/me] lookup failed:', error);
    return res.status(500).json({
      error: 'user_lookup_failed',
      message: 'Could not look up the user.',
    });
  }
});

/**
 * PUT /api/v1/core/users/me
 * Protected by verifyAppKey + verifyFirebaseToken.
 * Accepts partial updates ({ name?, email?, preferredLanguage? }).
 * Updates the matching row by firebase_uid.
 */
router.put('/users/me', verifyAppKey, verifyFirebaseToken, async (req, res) => {
  const uid = req.uid;
  const name = str(req.body?.name);
  const email = str(req.body?.email);
  const preferredLanguage = str(req.body?.preferredLanguage);

  const updates = [];
  const args = [];

  if (name !== null) {
    updates.push('name = ?');
    args.push(name);
  }
  if (email !== null) {
    updates.push('email = ?');
    args.push(email);
  }
  if (preferredLanguage !== null) {
    updates.push('preferred_language = ?');
    args.push(preferredLanguage);
  }

  if (updates.length === 0) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Body must include at least one of "name", "email", or "preferredLanguage".',
    });
  }

  const now = Date.now();
  updates.push('updated_at = ?');
  args.push(now);
  args.push(uid);

  try {
    const check = await db.execute({
      sql: 'SELECT id FROM users WHERE firebase_uid = ?',
      args: [uid],
    });

    if (check.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    await db.execute({
      sql: `UPDATE users SET ${updates.join(', ')} WHERE firebase_uid = ?`,
      args,
    });

    const row = await db.execute({
      sql: 'SELECT * FROM users WHERE firebase_uid = ?',
      args: [uid],
    });

    return res.status(200).json({ user: row.rows[0] });
  } catch (error) {
    console.error('[core/users/me] update failed:', error);
    return res.status(500).json({
      error: 'user_update_failed',
      message: 'Could not update the user.',
    });
  }
});

export default router;
