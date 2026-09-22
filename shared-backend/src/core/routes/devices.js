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
 * POST /api/v1/core/devices
 * Generic device-token registration — requires BOTH an app key (which app)
 * and a Firebase ID token (which user). Upserts into device_tokens keyed by
 * (app_id, uid, role).
 *
 * Body: { role: string, fcmToken: string }
 */
router.post('/devices', verifyAppKey, verifyFirebaseToken, async (req, res) => {
  const role = str(req.body?.role);
  const fcmToken = str(req.body?.fcmToken);

  if (!role || !fcmToken) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Body must include non-empty "role" and "fcmToken" strings.',
    });
  }

  const appId = req.appId;
  const uid = req.uid;

  try {
    const existing = await db.execute({
      sql: 'SELECT id FROM device_tokens WHERE app_id = ? AND uid = ? AND role = ?',
      args: [appId, uid, role],
    });

    const now = Date.now();
    let deviceId;

    if (existing.rows.length > 0) {
      deviceId = existing.rows[0].id;
      await db.execute({
        sql: 'UPDATE device_tokens SET fcm_token = ?, updated_at = ? WHERE id = ?',
        args: [fcmToken, now, deviceId],
      });
    } else {
      deviceId = randomUUID();
      await db.execute({
        sql: `INSERT INTO device_tokens (id, app_id, uid, role, fcm_token, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)`,
        args: [deviceId, appId, uid, role, fcmToken, now],
      });
    }

    return res.status(200).json({
      success: true,
      device: { id: deviceId, appId, uid, role, updatedAt: now },
    });
  } catch (error) {
    console.error('[core/devices] upsert failed:', error);
    return res.status(500).json({
      error: 'device_registration_failed',
      message: 'Could not register the device token.',
    });
  }
});

export default router;
