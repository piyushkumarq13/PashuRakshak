import { createHmac, timingSafeEqual } from 'node:crypto';
import { env } from '../../config/env.js';

/**
 * Stateless HMAC-signed session tokens (JWT-like, dependency-free).
 *
 * Format: base64url(jsonPayload) "." base64url(hmacSHA256(payload, secret))
 *
 * Session tokens:  { typ: 'session', sub, role, phone?, email?, district?, exp }
 * Verify tokens:   { typ: 'verify', purpose, email, phone?, exp }  (short-lived,
 * returned by OTP verification and consumed while completing registration).
 */

export const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days
export const VERIFY_TTL_MS = 10 * 60 * 1000; // 10 minutes

function sign(payloadB64) {
  return createHmac('sha256', env.sessionSecret).update(payloadB64).digest('base64url');
}

/** Issues a signed token with payload.exp = now + ttlMs. */
export function issueToken(payload, ttlMs = SESSION_TTL_MS) {
  const body = { ...payload, exp: Date.now() + ttlMs };
  const payloadB64 = Buffer.from(JSON.stringify(body), 'utf8').toString('base64url');
  return `${payloadB64}.${sign(payloadB64)}`;
}

/** Returns the decoded payload, or null when invalid/expired/tampered. */
export function verifyToken(token) {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 2) return null;

  const [payloadB64, signature] = parts;
  if (!payloadB64 || !signature) return null;

  const expected = Buffer.from(sign(payloadB64), 'utf8');
  const provided = Buffer.from(signature, 'utf8');
  if (expected.length !== provided.length || !timingSafeEqual(expected, provided)) {
    return null;
  }

  let payload;
  try {
    payload = JSON.parse(Buffer.from(payloadB64, 'base64url').toString('utf8'));
  } catch {
    return null;
  }

  if (!payload || typeof payload !== 'object') return null;
  if (typeof payload.exp !== 'number' || payload.exp < Date.now()) return null;
  return payload;
}
