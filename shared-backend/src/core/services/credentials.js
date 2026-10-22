import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';

/**
 * PIN / password hashing with scrypt (node:crypto, no external dependency).
 * Stored format: "scrypt:<salt-hex>:<hash-hex>"
 */

const scryptAsync = promisify(scrypt);

export async function hashCredential(credential) {
  const salt = randomBytes(16).toString('hex');
  const hash = await scryptAsync(String(credential), salt, 32);
  return `scrypt:${salt}:${hash.toString('hex')}`;
}

export async function verifyCredential(credential, stored) {
  if (!stored || typeof stored !== 'string') return false;
  const parts = stored.split(':');
  if (parts.length !== 3 || parts[0] !== 'scrypt') return false;

  const [, salt, hashHex] = parts;
  let expected;
  try {
    expected = Buffer.from(hashHex, 'hex');
  } catch {
    return false;
  }
  if (expected.length === 0) return false;

  const candidate = await scryptAsync(String(credential), salt, expected.length);
  return candidate.length === expected.length && timingSafeEqual(candidate, expected);
}

/** Validates a user-chosen credential. Returns an error message, or null when valid. */
export function validateCredential(credential, pinType) {
  const value = String(credential ?? '');
  if (pinType === 'pin') {
    return /^\d{6}$/.test(value) ? null : 'PIN must be exactly 6 digits.';
  }
  if (pinType === 'password') {
    return value.length >= 6 ? null : 'Password must be at least 6 characters.';
  }
  return 'Credential type must be "pin" or "password".';
}
