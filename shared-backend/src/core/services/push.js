import { getMessaging } from 'firebase-admin/messaging';
import { db } from '../../db/client.js';
import { ensureFirebase } from '../middleware/verifyFirebaseToken.js';

/**
 * Generic push helpers (core — reusable by any module).
 * Looks the user's FCM token(s) up in device_tokens and sends via
 * firebase-admin messaging.
 */

/**
 * Sends a push notification to every FCM token registered for [uid].
 * Returns { uid, sent, failureCount } — or { skipped: 'no_tokens' }.
 * Throws if Firebase is not configured; individual token failures are
 * reported in failureCount, not thrown.
 */
export async function sendPushToUid(uid, title, body) {
  ensureFirebase();

  const tokensResult = await db.execute({
    sql: `SELECT fcm_token FROM device_tokens
      WHERE uid = ? AND fcm_token IS NOT NULL AND fcm_token != ''`,
    args: [uid],
  });

  const tokens = tokensResult.rows.map((row) => row.fcm_token);
  if (tokens.length === 0) {
    return { uid, sent: 0, failureCount: 0, skipped: 'no_tokens' };
  }

  const result = await getMessaging().sendEachForMulticast({
    notification: { title, body },
    data: { title, body },
    tokens,
  });

  return { uid, sent: result.successCount, failureCount: result.failureCount };
}
