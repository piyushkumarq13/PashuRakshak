import { Router } from 'express';
import { db } from '../../db/client.js';
import { verifyAppKey } from '../middleware/verifyAppKey.js';
import { requireRole, verifySession } from '../middleware/verifySession.js';

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
 * Updates the *authenticated* user's profile only.
 *
 * The old implementation upserted by an arbitrary phone/role from the body,
 * which let any signed-in account overwrite or create another account. The
 * session is now authoritative; clients may only send their own phone/role.
 */
router.post('/users', verifyAppKey, verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  if (!req.userId) {
    return res.status(401).json({ error: 'invalid_session', message: 'Session has no user id.' });
  }

  const phone = str(req.body?.phone);
  const role = str(req.body?.role);
  const name = str(req.body?.name);
  const email = str(req.body?.email);
  const preferredLanguage = str(req.body?.preferredLanguage);

  const now = Date.now();

  try {
    const existing = await db.execute({
      sql: 'SELECT id, phone, role FROM users WHERE id = ?',
      args: [req.userId],
    });
    const current = existing.rows[0];
    if (!current) {
      return res.status(404).json({ error: 'user_not_found', message: 'Signed-in user no longer exists.' });
    }

    if (phone && phone !== current.phone) {
      return res.status(403).json({
        error: 'phone_mismatch',
        message: 'You can only update the account tied to this session.',
      });
    }
    if (role && role !== current.role) {
      return res.status(403).json({
        error: 'role_mismatch',
        message: 'A session cannot change its own role.',
      });
    }

    await db.execute({
      sql: `UPDATE users SET name = ?, email = ?, preferred_language = ?, updated_at = ? WHERE id = ?`,
      args: [name, email, preferredLanguage, now, req.userId],
    });

    const row = await db.execute({
      sql: 'SELECT * FROM users WHERE id = ?',
      args: [req.userId],
    });

    return res.status(200).json({ user: publicUser(row.rows[0]) });
  } catch (error) {
    console.error('[core/users] update failed:', error);
    return res.status(500).json({
      error: 'user_upsert_failed',
      message: 'Could not update the user.',
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
