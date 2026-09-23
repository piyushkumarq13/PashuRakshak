import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../db/client.js';
import { hashCredential, validateCredential, verifyCredential } from '../services/credentials.js';
import {
  createOtp,
  isValidEmail,
  maskEmail,
  normalizeEmail,
  sendOtpError,
  verifyOtp,
} from '../services/otp.js';
import { canonicalPhone, isValidPhone } from '../services/phone.js';
import { issueToken, verifyToken } from '../services/sessionToken.js';

/**
 * Custom authentication — NO Firebase Auth.
 *
 * OTP (emailed, 6-digit) verifies email ownership; a 6-digit PIN or password
 * (user's choice) authenticates day-to-day logins. Sessions are backend-signed
 * tokens returned as { token } and sent as `Authorization: Bearer <token>`.
 *
 * Flows:
 *   Farmer: check phone → (exists ? login with PIN : register:
 *           OTP → details + PIN → session) ; forgot-PIN via OTP.
 *   Vet:    web only — phone + email → OTP → set PIN/password → session →
 *           submit application → magistrate approval → vet-app login.
 *   Gov:    magistrate seeded by admin → email OTP → session.
 */

const router = Router();

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value).trim();
  return text === '' ? null : text;
}

function publicUser(row) {
  if (!row) return null;
  const { pin_hash: _pinHash, firebase_uid: _firebaseUid, ...rest } = row;
  return rest;
}

function sessionTokenFor(user) {
  return issueToken({
    typ: 'session',
    sub: user.id,
    role: user.role,
    phone: user.phone ?? null,
    email: user.email ?? null,
  });
}

async function findUserByPhone(phone) {
  const result = await db.execute({
    sql: 'SELECT * FROM users WHERE phone = ?',
    args: [phone],
  });
  return result.rows[0] ?? null;
}

async function getVetApplication(userId) {
  const result = await db.execute({
    sql: 'SELECT * FROM vet_applications WHERE user_id = ?',
    args: [userId],
  });
  return result.rows[0] ?? null;
}

async function getFarmerProfile(userId) {
  const result = await db.execute({
    sql: 'SELECT * FROM pashu_farmer_profiles WHERE user_id = ?',
    args: [userId],
  });
  return result.rows[0] ?? null;
}

/** Consumes a verify token issued by POST /auth/otp/verify. Returns payload or throws. */
function requireVerifyToken(token, purpose) {
  const payload = verifyToken(token);
  if (!payload || payload.typ !== 'verify' || payload.purpose !== purpose) {
    const err = { status: 401, error: 'invalid_verify_token', message: 'Verification expired. Start again.' };
    throw err;
  }
  return payload;
}

/* ───────────────────────── OTP ───────────────────────── */

/**
 * POST /api/v1/auth/otp/send
 * Body: { email, purpose, phone? }
 * purpose: vet_register | farmer_register | farmer_reset_pin | vet_reset_pin
 * (gov_login must use POST /auth/gov/request-otp — magistrate existence is checked first.)
 */
router.post('/otp/send', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const purpose = str(req.body?.purpose);
  const phone = str(req.body?.phone);

  if (!isValidEmail(email)) {
    return res.status(400).json({ error: 'invalid_email', message: 'A valid email address is required.' });
  }
  if (!purpose) {
    return res.status(400).json({ error: 'invalid_body', message: 'Body must include "purpose".' });
  }
  if (purpose === 'gov_login') {
    return res.status(400).json({ error: 'invalid_purpose', message: 'Use /auth/gov/request-otp for government sign-in.' });
  }

  try {
    const info = await createOtp(email, purpose, { phone });
    return res.status(200).json({
      success: true,
      email: maskEmail(email),
      purpose,
      ...info,
      message: 'A 6-digit code has been sent to your email.',
    });
  } catch (error) {
    if (error?.status) return sendOtpError(res, error);
    console.error('[auth/otp/send] failed:', error);
    return res.status(500).json({ error: 'otp_send_failed', message: 'Could not send the code. Try again.' });
  }
});

/**
 * POST /api/v1/auth/otp/verify
 * Body: { email, purpose, code } → { verifyToken } (10-minute signed token).
 */
router.post('/otp/verify', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const purpose = str(req.body?.purpose);
  const code = str(req.body?.code);

  if (!purpose) {
    return res.status(400).json({ error: 'invalid_body', message: 'Body must include "purpose".' });
  }

  try {
    const row = await verifyOtp(email, purpose, code);
    const verifyTokenValue = issueToken(
      { typ: 'verify', purpose, email, phone: row.phone ?? null },
      10 * 60 * 1000,
    );
    return res.status(200).json({ success: true, verifyToken: verifyTokenValue, email: maskEmail(email) });
  } catch (error) {
    if (error?.status) return sendOtpError(res, error);
    console.error('[auth/otp/verify] failed:', error);
    return res.status(500).json({ error: 'otp_verify_failed', message: 'Could not verify the code.' });
  }
});

/* ───────────────────────── Vet (web registration + app login) ───────────────────────── */

/**
 * POST /api/v1/auth/vet/setup-pin
 * Body: { verifyToken, phone, pin, pinType: 'pin'|'password' }
 * Creates the vet's users row after email verification (no application yet —
 * that is submitted next). Returns a session token for the registration web.
 */
router.post('/vet/setup-pin', async (req, res) => {
  let verified;
  try {
    verified = requireVerifyToken(req.body?.verifyToken, 'vet_register');
  } catch (error) {
    return res.status(error.status).json({ error: error.error, message: error.message });
  }

  const phone = canonicalPhone(req.body?.phone);
  const pinType = str(req.body?.pinType) ?? 'pin';
  const credential = str(req.body?.pin);

  if (!isValidPhone(phone)) {
    return res.status(400).json({ error: 'invalid_phone', message: 'Enter a valid 10-digit phone number.' });
  }
  const credError = validateCredential(credential, pinType);
  if (credError) {
    return res.status(400).json({ error: 'invalid_credential', message: credError });
  }

  try {
    const existing = await findUserByPhone(phone);

    if (existing && existing.role !== 'vet') {
      return res.status(409).json({
        error: 'phone_in_use',
        message: 'This phone number is already registered with a different account.',
      });
    }

    if (existing) {
      const application = await getVetApplication(existing.id);
      if (application) {
        return res.status(409).json({
          error: 'already_registered',
          applicationStatus: application.status,
          message: 'An application already exists for this phone number. Sign in to view its status.',
        });
      }
      // Only allow completing when the stored email is unset or matches the
      // address that was just verified (prevents hijacking legacy accounts).
      const storedEmail = existing.email ? normalizeEmail(existing.email) : null;
      if (storedEmail && storedEmail !== verified.email) {
        return res.status(409).json({
          error: 'email_mismatch',
          message: 'This phone number is registered with a different email address.',
        });
      }
      // Vet user created earlier but application never submitted — allow re-setup.
      await db.execute({
        sql: `UPDATE users SET email = ?, email_verified = 1, pin_hash = ?, pin_type = ?, updated_at = ? WHERE id = ?`,
        args: [verified.email, hashCredential(credential), pinType, Date.now(), existing.id],
      });
    } else {
      const now = Date.now();
      await db.execute({
        sql: `INSERT INTO users (id, phone, role, email, email_verified, pin_hash, pin_type, preferred_language, created_at, updated_at)
          VALUES (?, ?, 'vet', ?, 1, ?, ?, 'hi', ?, ?)`,
        args: [randomUUID(), phone, verified.email, hashCredential(credential), pinType, now, now],
      });
    }

    const user = await findUserByPhone(phone);
    return res.status(200).json({ token: sessionTokenFor(user), user: publicUser(user) });
  } catch (error) {
    console.error('[auth/vet/setup-pin] failed:', error);
    return res.status(500).json({ error: 'setup_failed', message: 'Could not set up your account.' });
  }
});

/**
 * POST /api/v1/auth/vet/login
 * Body: { phone, credential, client: 'app' | 'web' }
 *
 * The vet app (client='app') can only log in once the application is approved.
 * The registration web (client='web') can always log in to view status.
 * Responses include applicationStatus for the web status page.
 */
router.post('/vet/login', async (req, res) => {
  const phone = canonicalPhone(req.body?.phone);
  const credential = str(req.body?.credential);
  const client = str(req.body?.client) ?? 'app';

  if (!isValidPhone(phone) || !credential) {
    return res.status(400).json({ error: 'invalid_body', message: 'Phone and credential are required.' });
  }

  try {
    const user = await findUserByPhone(phone);
    if (!user || user.role !== 'vet' || !user.pin_hash) {
      return res.status(404).json({ error: 'user_not_found', message: 'No vet account found for this phone number.' });
    }
    if (!verifyCredential(credential, user.pin_hash)) {
      return res.status(401).json({ error: 'invalid_credential', message: 'Incorrect PIN or password.' });
    }

    const application = await getVetApplication(user.id);
    const applicationStatus = application?.status ?? 'none';

    if (client === 'app' && applicationStatus !== 'approved') {
      return res.status(403).json({
        error: 'not_approved',
        applicationStatus,
        reviewNote: application?.review_note ?? null,
        message:
          applicationStatus === 'pending'
            ? 'Your application is still under review by the District Magistrate.'
            : applicationStatus === 'rejected'
              ? 'Your application was rejected. Check the registration website for details.'
              : 'You have not submitted an application yet. Register on the vet registration website.',
      });
    }

    return res.status(200).json({
      token: sessionTokenFor(user),
      user: publicUser(user),
      applicationStatus,
      application: application ?? null,
    });
  } catch (error) {
    console.error('[auth/vet/login] failed:', error);
    return res.status(500).json({ error: 'login_failed', message: 'Could not sign in. Try again.' });
  }
});

/**
 * POST /api/v1/auth/vet/reset-pin
 * Body: { verifyToken (purpose vet_reset_pin), pin, pinType }
 */
router.post('/vet/reset-pin', async (req, res) => {
  let verified;
  try {
    verified = requireVerifyToken(req.body?.verifyToken, 'vet_reset_pin');
  } catch (error) {
    return res.status(error.status).json({ error: error.error, message: error.message });
  }

  const pinType = str(req.body?.pinType) ?? 'pin';
  const credential = str(req.body?.pin);
  const credError = validateCredential(credential, pinType);
  if (credError) {
    return res.status(400).json({ error: 'invalid_credential', message: credError });
  }

  try {
    const result = await db.execute({
      sql: `SELECT * FROM users WHERE email = ? AND role = 'vet'`,
      args: [verified.email],
    });
    const user = result.rows[0];
    if (!user) {
      return res.status(404).json({ error: 'user_not_found', message: 'No vet account found for this email.' });
    }

    await db.execute({
      sql: 'UPDATE users SET pin_hash = ?, pin_type = ?, updated_at = ? WHERE id = ?',
      args: [hashCredential(credential), pinType, Date.now(), user.id],
    });

    const updated = await db.execute({ sql: 'SELECT * FROM users WHERE id = ?', args: [user.id] });
    const fresh = updated.rows[0];
    return res.status(200).json({ token: sessionTokenFor(fresh), user: publicUser(fresh) });
  } catch (error) {
    console.error('[auth/vet/reset-pin] failed:', error);
    return res.status(500).json({ error: 'reset_failed', message: 'Could not reset the PIN.' });
  }
});

/* ───────────────────────── Farmer ───────────────────────── */

/** POST /api/v1/auth/farmer/check  Body: { phone } → { exists, hasCredential } */
router.post('/farmer/check', async (req, res) => {
  const phone = canonicalPhone(req.body?.phone);
  if (!isValidPhone(phone)) {
    return res.status(400).json({ error: 'invalid_phone', message: 'Enter a valid 10-digit phone number.' });
  }

  try {
    const user = await findUserByPhone(phone);
    const exists = !!user && user.role === 'farmer';
    return res.status(200).json({
      exists,
      hasCredential: exists && !!user.pin_hash,
      // Users that predate the PIN flow must set a PIN via email OTP first.
      requiresPinSetup: exists && !user.pin_hash,
      hasEmail: exists && !!user.email,
    });
  } catch (error) {
    console.error('[auth/farmer/check] failed:', error);
    return res.status(500).json({ error: 'check_failed', message: 'Could not check the account.' });
  }
});

/**
 * POST /api/v1/auth/farmer/login  Body: { phone, pin }
 * → { token, user, profile }
 */
router.post('/farmer/login', async (req, res) => {
  const phone = canonicalPhone(req.body?.phone);
  const pin = str(req.body?.pin);

  if (!isValidPhone(phone) || !pin) {
    return res.status(400).json({ error: 'invalid_body', message: 'Phone and PIN are required.' });
  }

  try {
    const user = await findUserByPhone(phone);
    if (!user || user.role !== 'farmer') {
      return res.status(404).json({ error: 'user_not_found', message: 'No account found for this phone number. Please register.' });
    }
    if (!user.pin_hash) {
      return res.status(403).json({
        error: 'pin_not_set',
        message: 'This account still needs a PIN. Use "Forgot PIN" to set one via email.',
      });
    }
    if (!verifyCredential(pin, user.pin_hash)) {
      return res.status(401).json({ error: 'invalid_credential', message: 'Incorrect PIN.' });
    }

    const profile = await getFarmerProfile(user.id);
    return res.status(200).json({ token: sessionTokenFor(user), user: publicUser(user), profile });
  } catch (error) {
    console.error('[auth/farmer/login] failed:', error);
    return res.status(500).json({ error: 'login_failed', message: 'Could not sign in. Try again.' });
  }
});

/**
 * POST /api/v1/auth/farmer/register
 * Body: { verifyToken (farmer_register), phone, pin, name, preferredLanguage?,
 *         animalCount?, village?, pincode? }
 * Creates (or completes) the user + farmer profile directly in the database.
 */
router.post('/farmer/register', async (req, res) => {
  let verified;
  try {
    verified = requireVerifyToken(req.body?.verifyToken, 'farmer_register');
  } catch (error) {
    return res.status(error.status).json({ error: error.error, message: error.message });
  }

  const phone = canonicalPhone(req.body?.phone);
  const pin = str(req.body?.pin);
  const name = str(req.body?.name);
  const preferredLanguage = str(req.body?.preferredLanguage) ?? 'hi';
  const animalCountRaw = req.body?.animalCount;
  const animalCount =
    animalCountRaw === undefined || animalCountRaw === null || animalCountRaw === ''
      ? null
      : Number(animalCountRaw);
  const village = str(req.body?.village);
  const pincode = str(req.body?.pincode);

  if (!isValidPhone(phone)) {
    return res.status(400).json({ error: 'invalid_phone', message: 'Enter a valid 10-digit phone number.' });
  }
  if (!name) {
    return res.status(400).json({ error: 'invalid_body', message: 'Name is required.' });
  }
  const credError = validateCredential(pin, 'pin');
  if (credError) {
    return res.status(400).json({ error: 'invalid_credential', message: credError });
  }
  if (animalCount !== null && !Number.isFinite(animalCount)) {
    return res.status(400).json({ error: 'invalid_body', message: 'animalCount must be a number.' });
  }
  if (!village || !pincode) {
    return res.status(400).json({ error: 'invalid_body', message: 'Village and pincode are required.' });
  }

  try {
    const now = Date.now();
    let user;

    const existing = await findUserByPhone(phone);
    if (existing && existing.role !== 'farmer') {
      return res.status(409).json({
        error: 'phone_in_use',
        message: 'This phone number is already registered with a different account.',
      });
    }

    if (existing) {
      // Completing a legacy account (or re-registering): only allow when the
      // stored email is unset or matches the address that just got verified.
      const storedEmail = existing.email ? normalizeEmail(existing.email) : null;
      if (storedEmail && storedEmail !== verified.email) {
        return res.status(409).json({
          error: 'email_mismatch',
          message: 'This phone number is registered with a different email address.',
        });
      }
      await db.execute({
        sql: `UPDATE users SET name = ?, email = ?, email_verified = 1, pin_hash = ?, pin_type = 'pin',
            preferred_language = ?, updated_at = ? WHERE id = ?`,
        args: [name, verified.email, hashCredential(pin), preferredLanguage, now, existing.id],
      });
      user = await findUserByPhone(phone);
    } else {
      const userId = randomUUID();
      await db.execute({
        sql: `INSERT INTO users (id, phone, role, name, email, email_verified, pin_hash, pin_type, preferred_language, created_at, updated_at)
          VALUES (?, ?, 'farmer', ?, ?, 1, ?, 'pin', ?, ?, ?)`,
        args: [userId, phone, name, verified.email, hashCredential(pin), preferredLanguage, now, now],
      });
      user = await findUserByPhone(phone);
    }

    // Farmer profile — saved straight to the database (no local-only storage).
    const existingProfile = await db.execute({
      sql: 'SELECT user_id FROM pashu_farmer_profiles WHERE user_id = ?',
      args: [user.id],
    });
    if (existingProfile.rows.length > 0) {
      await db.execute({
        sql: 'UPDATE pashu_farmer_profiles SET animal_count = ?, village = ?, pincode = ? WHERE user_id = ?',
        args: [animalCount, village, pincode, user.id],
      });
    } else {
      await db.execute({
        sql: `INSERT INTO pashu_farmer_profiles (user_id, animal_count, village, pincode, created_at)
          VALUES (?, ?, ?, ?, ?)`,
        args: [user.id, animalCount, village, pincode, now],
      });
    }

    const profile = await getFarmerProfile(user.id);
    return res.status(200).json({ token: sessionTokenFor(user), user: publicUser(user), profile });
  } catch (error) {
    console.error('[auth/farmer/register] failed:', error);
    return res.status(500).json({ error: 'register_failed', message: 'Could not create the account.' });
  }
});

/** POST /api/v1/auth/farmer/reset-pin  Body: { verifyToken (farmer_reset_pin), pin } */
router.post('/farmer/reset-pin', async (req, res) => {
  let verified;
  try {
    verified = requireVerifyToken(req.body?.verifyToken, 'farmer_reset_pin');
  } catch (error) {
    return res.status(error.status).json({ error: error.error, message: error.message });
  }

  const pin = str(req.body?.pin);
  const credError = validateCredential(pin, 'pin');
  if (credError) {
    return res.status(400).json({ error: 'invalid_credential', message: credError });
  }

  try {
    const result = await db.execute({
      sql: `SELECT * FROM users WHERE email = ? AND role = 'farmer'`,
      args: [verified.email],
    });
    const user = result.rows[0];
    if (!user) {
      return res.status(404).json({ error: 'user_not_found', message: 'No farmer account found for this email.' });
    }

    await db.execute({
      sql: `UPDATE users SET pin_hash = ?, pin_type = 'pin', email_verified = 1, updated_at = ? WHERE id = ?`,
      args: [hashCredential(pin), Date.now(), user.id],
    });

    const updated = await db.execute({ sql: 'SELECT * FROM users WHERE id = ?', args: [user.id] });
    const fresh = updated.rows[0];
    const profile = await getFarmerProfile(fresh.id);
    return res.status(200).json({ token: sessionTokenFor(fresh), user: publicUser(fresh), profile });
  } catch (error) {
    console.error('[auth/farmer/reset-pin] failed:', error);
    return res.status(500).json({ error: 'reset_failed', message: 'Could not reset the PIN.' });
  }
});

/* ───────────────────────── Government / District Magistrate ───────────────────────── */

/** POST /api/v1/auth/gov/request-otp  Body: { email } */
router.post('/gov/request-otp', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  if (!isValidEmail(email)) {
    return res.status(400).json({ error: 'invalid_email', message: 'A valid email address is required.' });
  }

  try {
    const result = await db.execute({ sql: 'SELECT id FROM gov_magistrates WHERE email = ?', args: [email] });
    if (result.rows.length === 0) {
      // Same shape as success to avoid account enumeration.
      return res.status(200).json({
        success: true,
        email: maskEmail(email),
        message: 'If this email is registered, a 6-digit code has been sent.',
      });
    }

    const info = await createOtp(email, 'gov_login');
    return res.status(200).json({
      success: true,
      email: maskEmail(email),
      ...info,
      message: 'A 6-digit code has been sent to your email.',
    });
  } catch (error) {
    if (error?.status) return sendOtpError(res, error);
    console.error('[auth/gov/request-otp] failed:', error);
    return res.status(500).json({ error: 'otp_send_failed', message: 'Could not send the code. Try again.' });
  }
});

/** POST /api/v1/auth/gov/verify  Body: { email, code } → { token, magistrate } */
router.post('/gov/verify', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const code = str(req.body?.code);

  try {
    await verifyOtp(email, 'gov_login', code);

    const result = await db.execute({ sql: 'SELECT * FROM gov_magistrates WHERE email = ?', args: [email] });
    const magistrate = result.rows[0];
    if (!magistrate) {
      return res.status(404).json({ error: 'not_found', message: 'No government account found for this email.' });
    }

    const token = issueToken({
      typ: 'session',
      sub: magistrate.id,
      role: 'gov',
      email: magistrate.email,
      district: magistrate.district,
    });

    return res.status(200).json({
      token,
      magistrate: {
        id: magistrate.id,
        email: magistrate.email,
        name: magistrate.name,
        district: magistrate.district,
        state: magistrate.state ?? null,
      },
    });
  } catch (error) {
    if (error?.status) return sendOtpError(res, error);
    console.error('[auth/gov/verify] failed:', error);
    return res.status(500).json({ error: 'otp_verify_failed', message: 'Could not verify the code.' });
  }
});

export default router;
