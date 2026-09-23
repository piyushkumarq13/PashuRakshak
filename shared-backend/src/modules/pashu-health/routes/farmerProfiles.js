import { Router } from 'express';
import { db } from '../../../db/client.js';
import { verifyFirebaseToken } from '../../../core/middleware/verifyFirebaseToken.js';

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

/**
 * POST /api/v1/pashu-health/farmer-profiles
 * Protected by verifyAppKey (router-level) + verifyFirebaseToken.
 * Looks up the user by req.uid (via users.firebase_uid), upserts into
 * pashu_farmer_profiles keyed by that user's id.
 */
router.post('/', verifyFirebaseToken, async (req, res) => {
  const uid = req.uid;
  const animalCount = num(req.body?.animalCount) ?? num(req.body?.animal_count);
  const village = str(req.body?.village);
  const pincode = str(req.body?.pincode);

  if (animalCount === null || village === null || pincode === null) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Fields "animalCount", "village", and "pincode" are required.',
    });
  }

  try {
    const userResult = await db.execute({
      sql: 'SELECT id FROM users WHERE firebase_uid = ?',
      args: [uid],
    });

    if (userResult.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    const userId = userResult.rows[0].id;
    const now = Date.now();

    const existing = await db.execute({
      sql: 'SELECT id FROM pashu_farmer_profiles WHERE user_id = ?',
      args: [userId],
    });

    if (existing.rows.length > 0) {
      await db.execute({
        sql: `UPDATE pashu_farmer_profiles SET animal_count = ?, village = ?, pincode = ?, created_at = ?
          WHERE user_id = ?`,
        args: [animalCount, village, pincode, now, userId],
      });
    } else {
      await db.execute({
        sql: `INSERT INTO pashu_farmer_profiles (user_id, animal_count, village, pincode, created_at)
          VALUES (?, ?, ?, ?, ?)`,
        args: [userId, animalCount, village, pincode, now],
      });
    }

    const profile = await db.execute({
      sql: 'SELECT * FROM pashu_farmer_profiles WHERE user_id = ?',
      args: [userId],
    });

    return res.status(200).json({ profile: profile.rows[0] });
  } catch (error) {
    console.error('[pashu-health/farmer-profiles] upsert failed:', error);
    return res.status(500).json({
      error: 'farmer_profile_failed',
      message: 'Could not save the farmer profile.',
    });
  }
});

/**
 * GET /api/v1/pashu-health/farmer-profiles/me
 * Protected by verifyAppKey + verifyFirebaseToken.
 * Returns the current farmer's profile or 404.
 */
router.get('/me', verifyFirebaseToken, async (req, res) => {
  const uid = req.uid;

  try {
    const userResult = await db.execute({
      sql: 'SELECT id FROM users WHERE firebase_uid = ?',
      args: [uid],
    });

    if (userResult.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    const profile = await db.execute({
      sql: 'SELECT * FROM pashu_farmer_profiles WHERE user_id = ?',
      args: [userResult.rows[0].id],
    });

    if (profile.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    return res.status(200).json({ profile: profile.rows[0] });
  } catch (error) {
    console.error('[pashu-health/farmer-profiles/me] lookup failed:', error);
    return res.status(500).json({
      error: 'farmer_profile_lookup_failed',
      message: 'Could not look up the farmer profile.',
    });
  }
});

export default router;
