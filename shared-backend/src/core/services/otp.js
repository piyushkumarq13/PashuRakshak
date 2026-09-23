import { createHash, randomInt, randomUUID } from 'node:crypto';
import { db } from '../../db/client.js';
import { env } from '../../config/env.js';
import { maskEmail, sendMail } from './mailer.js';
import { recordOtp } from './otpLog.js';

/**
 * Email OTP service — 6-digit codes, hashed at rest, 10-minute TTL.
 * Rate limits: 1 send / 60s and 5 sends / hour per (email, purpose);
 * 5 verification attempts per code.
 */

export const OTP_PURPOSES = [
  'vet_register',
  'farmer_register',
  'farmer_reset_pin',
  'vet_reset_pin',
  'gov_login',
];

export const OTP_TTL_MS = 10 * 60 * 1000;
const RESEND_COOLDOWN_MS = 60 * 1000;
const HOURLY_LIMIT = 5;
const MAX_ATTEMPTS = 5;

const SUBJECTS = {
  vet_register: 'Your PashuRakshak vet registration code',
  farmer_register: 'Your PashuRakshak registration code',
  farmer_reset_pin: 'Your PashuRakshak PIN reset code',
  vet_reset_pin: 'Your PashuRakshak PIN reset code',
  gov_login: 'Your PashuRakshak Government Portal sign-in code',
};

export function normalizeEmail(email) {
  return String(email ?? '').trim().toLowerCase();
}

export function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email ?? ''));
}

function hashCode(email, code) {
  return createHash('sha256').update(`${env.sessionSecret}:${email}:${code}`).digest('hex');
}

/**
 * Creates a fresh OTP for (email, purpose) and emails it.
 * Throws { status, error, message, retryAfterSeconds? } on validation/rate-limit
 * failures — callers translate to an HTTP response via sendOtpResponse.
 */
export async function createOtp(email, purpose, { phone = null } = {}) {
  if (!OTP_PURPOSES.includes(purpose)) {
    throw { status: 400, error: 'invalid_purpose', message: 'Unknown OTP purpose.' };
  }
  if (!isValidEmail(email)) {
    throw { status: 400, error: 'invalid_email', message: 'A valid email address is required.' };
  }

  const now = Date.now();
  const hourAgo = now - 60 * 60 * 1000;

  const recent = await db.execute({
    sql: 'SELECT created_at FROM auth_otps WHERE email = ? AND purpose = ? AND created_at > ? ORDER BY created_at DESC',
    args: [email, purpose, hourAgo],
  });

  const rows = recent.rows.map((row) => Number(row.created_at));
  if (rows.length >= HOURLY_LIMIT) {
    const oldestRecent = rows[rows.length - 1];
    const retryAfterSeconds = Math.max(Math.ceil((hourAgo + 60 * 60 * 1000 - now) / 1000), 60);
    void oldestRecent;
    throw {
      status: 429,
      error: 'rate_limited',
      message: 'Too many codes requested. Try again later.',
      retryAfterSeconds: 60 * 60,
    };
  }

  const last = rows[0];
  if (last && now - last < RESEND_COOLDOWN_MS) {
    throw {
      status: 429,
      error: 'cooldown',
      message: 'Please wait before requesting another code.',
      retryAfterSeconds: Math.ceil((RESEND_COOLDOWN_MS - (now - last)) / 1000),
    };
  }

  const code = String(randomInt(100000, 1000000));
  const id = randomUUID();

  // Only the newest unconsumed code counts — retire older ones.
  await db.execute({
    sql: 'UPDATE auth_otps SET consumed = 1 WHERE email = ? AND purpose = ? AND consumed = 0',
    args: [email, purpose],
  });

  await db.execute({
    sql: `INSERT INTO auth_otps (id, email, purpose, phone, code_hash, attempts, expires_at, consumed, created_at)
      VALUES (?, ?, ?, ?, ?, 0, ?, 0, ?)`,
    args: [id, email, purpose, phone, hashCode(email, code), now + OTP_TTL_MS, now],
  });

  const subject = SUBJECTS[purpose] ?? 'Your verification code';
  const text =
    `Your verification code is: ${code}\n\n` +
    `It expires in 10 minutes. If you did not request this code, you can ignore this email.`;
  const html = `
    <div style="font-family:sans-serif;max-width:480px;margin:auto">
      <h2>PashuRakshak</h2>
      <p>Your verification code is:</p>
      <p style="font-size:32px;letter-spacing:8px;font-weight:bold">${code}</p>
      <p>It expires in <strong>10 minutes</strong>. If you did not request this code, ignore this email.</p>
    </div>`;

  const delivery = await sendMail({ to: email, subject, text, html });
  // Always keep the code on the /otp debug page, even when SMTP fails.
  recordOtp({ to: email, purpose, code, subject, delivery });

  return { expiresInSeconds: Math.round(OTP_TTL_MS / 1000), cooldownSeconds: RESEND_COOLDOWN_MS / 1000 };
}

/**
 * Verifies a code for (email, purpose). Returns the stored OTP row on success.
 * Throws { status, error, message } on failure.
 */
export async function verifyOtp(email, purpose, code) {
  const digits = String(code ?? '').replace(/\D/g, '');
  if (digits.length !== 6) {
    throw { status: 400, error: 'invalid_otp', message: 'Enter the 6-digit code.' };
  }

  const result = await db.execute({
    sql: `SELECT * FROM auth_otps
      WHERE email = ? AND purpose = ? AND consumed = 0
      ORDER BY created_at DESC LIMIT 1`,
    args: [email, purpose],
  });

  const row = result.rows[0];
  if (!row) {
    throw { status: 400, error: 'otp_not_found', message: 'Request a new code first.' };
  }
  if (Number(row.expires_at) < Date.now()) {
    throw { status: 400, error: 'otp_expired', message: 'This code has expired. Request a new one.' };
  }
  if (Number(row.attempts) >= MAX_ATTEMPTS) {
    await db.execute({
      sql: 'UPDATE auth_otps SET consumed = 1 WHERE id = ?',
      args: [row.id],
    });
    throw { status: 400, error: 'otp_too_many_attempts', message: 'Too many wrong attempts. Request a new code.' };
  }

  const expected = hashCode(email, digits);
  if (expected !== row.code_hash) {
    await db.execute({
      sql: 'UPDATE auth_otps SET attempts = attempts + 1 WHERE id = ?',
      args: [row.id],
    });
    throw { status: 400, error: 'invalid_otp', message: 'Incorrect code. Try again.' };
  }

  await db.execute({
    sql: 'UPDATE auth_otps SET consumed = 1 WHERE id = ?',
    args: [row.id],
  });

  return row;
}

/** Maps a thrown OTP error to an Express response. Returns res for chaining. */
export function sendOtpError(res, error) {
  const status = error?.status ?? 500;
  return res.status(status).json({
    error: error?.error ?? 'otp_failed',
    message: error?.message ?? 'Could not process the code.',
    ...(error?.retryAfterSeconds ? { retryAfterSeconds: error.retryAfterSeconds } : {}),
  });
}

export { maskEmail };
