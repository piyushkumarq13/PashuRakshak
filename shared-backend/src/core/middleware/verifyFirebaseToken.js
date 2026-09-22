import { cert, getApps, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

/**
 * Firebase Auth verification (core — reusable by any module).
 *
 * Reads FIREBASE_SERVICE_ACCOUNT_JSON from the environment, initializes
 * firebase-admin (lazily, so the server can start without it), verifies the
 * `Authorization: Bearer <idToken>` header, and attaches req.uid.
 */

let configError = 'FIREBASE_SERVICE_ACCOUNT_JSON has not been checked yet';

/** Initializes firebase-admin once. Throws with a clear message if unconfigured/invalid. */
export function ensureFirebase() {
  if (getApps().length > 0) return;

  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw || !raw.trim()) {
    configError =
      'FIREBASE_SERVICE_ACCOUNT_JSON is not set. Add the service account JSON ' +
      '(Firebase Console → Project Settings → Service Accounts) to your .env.';
    throw new Error(configError);
  }

  try {
    const serviceAccount = JSON.parse(raw.trim());
    initializeApp({ credential: cert(serviceAccount) });
    configError = null;
  } catch (error) {
    configError = `Invalid FIREBASE_SERVICE_ACCOUNT_JSON: ${error.message}`;
    throw new Error(configError);
  }
}

/**
 * Express middleware: requires a valid Firebase ID token.
 *  - 503 firebase_not_configured — service account missing/unparseable
 *  - 401 missing_bearer_token   — no Authorization: Bearer header
 *  - 401 invalid_firebase_token — token failed verification
 * On success sets req.uid (Firebase UID).
 */
export async function verifyFirebaseToken(req, res, next) {
  try {
    ensureFirebase();
  } catch (error) {
    return res.status(503).json({
      error: 'firebase_not_configured',
      message: error.message,
    });
  }

  const header = req.get('Authorization') ?? '';
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  if (!match) {
    return res.status(401).json({
      error: 'missing_bearer_token',
      message: 'Missing Authorization: Bearer <idToken> header.',
    });
  }

  try {
    const decoded = await getAuth().verifyIdToken(match[1]);
    req.uid = decoded.uid;
    return next();
  } catch (error) {
    console.warn('[verifyFirebaseToken] token rejected:', error?.message ?? error);
    return res.status(401).json({
      error: 'invalid_firebase_token',
      message: 'Firebase ID token is invalid or expired.',
    });
  }
}
