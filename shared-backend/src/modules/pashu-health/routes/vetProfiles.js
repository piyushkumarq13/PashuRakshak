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
 * POST /api/v1/pashu-health/vet-profiles
 * Protected by verifyAppKey + verifyFirebaseToken.
 * Accepts { pincode, serviceAreas: string[] }.
 * Upserts into pashu_vet_profiles keyed by the authenticated user's id
 * (via firebase_uid lookup).
 */
router.post('/', verifyFirebaseToken, async (req, res) => {
  const uid = req.uid;
  const pincode = str(req.body?.pincode);
  const serviceAreas = req.body?.serviceAreas;

  if (pincode === null || !Array.isArray(serviceAreas)) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Fields "pincode" and "serviceAreas" (array of strings) are required.',
    });
  }

  const serviceAreasStr = JSON.stringify(serviceAreas);

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
      sql: 'SELECT id FROM pashu_vet_profiles WHERE user_id = ?',
      args: [userId],
    });

    if (existing.rows.length > 0) {
      await db.execute({
        sql: `UPDATE pashu_vet_profiles SET pincode = ?, service_areas = ?, updated_at = ?
          WHERE user_id = ?`,
        args: [pincode, serviceAreasStr, now, userId],
      });
    } else {
      await db.execute({
        sql: `INSERT INTO pashu_vet_profiles (user_id, pincode, service_areas, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?)`,
        args: [userId, pincode, serviceAreasStr, now, now],
      });
    }

    const profile = await db.execute({
      sql: 'SELECT * FROM pashu_vet_profiles WHERE user_id = ?',
      args: [userId],
    });

    return res.status(200).json({ profile: profile.rows[0] });
  } catch (error) {
    console.error('[pashu-health/vet-profiles] upsert failed:', error);
    return res.status(500).json({
      error: 'vet_profile_failed',
      message: 'Could not save the vet profile.',
    });
  }
});

/**
 * GET /api/v1/pashu-health/vet-profiles/me
 * Protected by verifyAppKey + verifyFirebaseToken.
 * Returns the current vet's own profile or 404.
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
      sql: 'SELECT * FROM pashu_vet_profiles WHERE user_id = ?',
      args: [userResult.rows[0].id],
    });

    if (profile.rows.length === 0) {
      return res.status(404).json({ exists: false });
    }

    return res.status(200).json({ profile: profile.rows[0] });
  } catch (error) {
    console.error('[pashu-health/vet-profiles/me] lookup failed:', error);
    return res.status(500).json({
      error: 'vet_profile_lookup_failed',
      message: 'Could not look up the vet profile.',
    });
  }
});

export default router;
