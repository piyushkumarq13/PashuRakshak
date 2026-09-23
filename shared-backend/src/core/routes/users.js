import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../db/client.js';
import { verifyAppKey } from '../middleware/verifyAppKey.js';
import { verifySession } from '../middleware/verifySession.js';

const router = Router();

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value);
  return text === '' ? null : text;
}

function publicUser(row) {
  if (!row) return null;
  const { pin_hash: _pinHash, firebase_uid: _firebaseUid, ...rest } = row;
  return rest;
}

/**
 * POST /api/v1/core/users
 * Protected by verifyAppKey + verifySession.
 * Accepts { phone, role, name?, email?, preferredLanguage? }.
 * Upserts into users keyed by phone. Returns the full user row (minus secrets).
 */
router.post('/users', verifyAppKey, verifySession, async (req, res) => {
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
        sql: `UPDATE users SET role = ?, name = ?, email = ?, preferred_language = ?, updated_at = ? WHERE phone = ?`,
        args: [role, name, email, preferredLanguage, now, phone],
      });
    } else {
      userId = randomUUID();
      await db.execute({
        sql: `INSERT INTO users (id, phone, role, name, email, preferred_language, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        args: [userId, phone, role, name, email, preferredLanguage, now, now],
      });
    }

    const row = await db.execute({
      sql: 'SELECT * FROM users WHERE id = ?',
      args: [userId],
    });

    return res.status(200).json({ user: publicUser(row.rows[0]) });
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
 * Protected by verifyAppKey + verifySession.
 * Looks up users by the session's user id.
 * Returns 404 { exists: false } if not found (signals "needs onboarding").
 */
router.get('/users/me', verifyAppKey, verifySession, async (req, res) => {
  if (!req.userId) {
    return res.status(404).json({ exists: false });
  }

  try {
    const result = await db.execute({
      sql: 'SELECT * FROM users WHERE id = ?',
      args: [req.userId],
    });

    if (result.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    return res.status(200).json({ user: publicUser(result.rows[0]) });
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
 * Protected by verifyAppKey + verifySession.
 * Accepts partial updates ({ name?, email?, preferredLanguage? }).
 */
router.put('/users/me', verifyAppKey, verifySession, async (req, res) => {
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
  args.push(req.userId);

  try {
    const check = await db.execute({
      sql: 'SELECT id FROM users WHERE id = ?',
      args: [req.userId],
    });

    if (check.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    await db.execute({
      sql: `UPDATE users SET ${updates.join(', ')} WHERE id = ?`,
      args,
    });

    const row = await db.execute({
      sql: 'SELECT * FROM users WHERE id = ?',
      args: [req.userId],
    });

    return res.status(200).json({ user: publicUser(row.rows[0]) });
  } catch (error) {
    console.error('[core/users/me] update failed:', error);
    return res.status(500).json({
      error: 'user_update_failed',
      message: 'Could not update the user.',
    });
  }
});

export default router;
