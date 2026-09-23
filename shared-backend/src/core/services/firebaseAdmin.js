import { cert, getApps, initializeApp } from 'firebase-admin/app';

/**
 * firebase-admin initialization — used ONLY for FCM push delivery.
 * Authentication no longer uses Firebase (custom OTP + session tokens instead),
 * so this is initialized lazily and only when a push is actually sent.
 */

export function ensureFirebase() {
  if (getApps().length > 0) return;

  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw || !raw.trim()) {
    throw new Error(
      'FIREBASE_SERVICE_ACCOUNT_JSON is not set. Add the service account JSON ' +
        '(Firebase Console → Project Settings → Service Accounts) to your .env.',
    );
  }

  try {
    const serviceAccount = JSON.parse(raw.trim());
    initializeApp({ credential: cert(serviceAccount) });
  } catch (error) {
    throw new Error(`Invalid FIREBASE_SERVICE_ACCOUNT_JSON: ${error.message}`);
  }
}
