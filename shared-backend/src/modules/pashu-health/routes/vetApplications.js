import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../../db/client.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';
import { isValidPhone } from '../../../core/services/phone.js';
import { resolveDistrict } from '../../../core/services/pincode.js';

/**
 * Vet registration applications — submitted from the vet registration website
 * after email verification + PIN setup, reviewed by the district magistrate
 * of the application's pincode.
 *
 * All routes: X-App-Key (router-level) + session token with role='vet'.
 */

const router = Router();

router.use(verifySession, requireRole('vet'));

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value).trim();
  return text === '' ? null : text;
}

function num(value, fallback = null) {
  if (value === undefined || value === null || value === '') return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

/**
 * POST /api/v1/pashu-health/vet-applications
 * Body: { fullName, qualification, licenseNumber, experienceYears?, clinicName?,
 *          address, village, pincode, serviceAreas: string[] }
 *
 * District/state are resolved from the pincode (postal API) and the application
 * is routed to that district's magistrate. Also upserts the vet's profile so
 * approved vets are immediately visible to farmers (vets/available) and for
 * auto-assignment.
 */
router.post('/', async (req, res) => {
  const body = req.body ?? {};
  const fullName = str(body.fullName);
  const qualification = str(body.qualification);
  const licenseNumber = str(body.licenseNumber);
  const experienceYears = num(body.experienceYears);
  const clinicName = str(body.clinicName);
  const address = str(body.address);
  const village = str(body.village);
  const pincode = str(body.pincode)?.replace(/\D/g, '') ?? null;
  const serviceAreas = body.serviceAreas;

  if (!fullName || !qualification || !licenseNumber || !address || !village) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'fullName, qualification, licenseNumber, address and village are required.',
    });
  }
  if (!pincode || !/^\d{6}$/.test(pincode)) {
    return res.status(400).json({ error: 'invalid_pincode', message: 'A valid 6-digit pincode is required.' });
  }
  if (!Array.isArray(serviceAreas) || serviceAreas.filter((a) => str(a)).length === 0) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'serviceAreas must be a non-empty array of coverage areas.',
    });
  }
  const cleanedAreas = serviceAreas.map((a) => String(a).trim()).filter(Boolean);

  try {
    const userResult = await db.execute({ sql: 'SELECT * FROM users WHERE id = ?', args: [req.userId] });
    const user = userResult.rows[0];
    if (!user) {
      return res.status(401).json({ error: 'invalid_session', message: 'Account not found. Sign in again.' });
    }

    const districtInfo = await resolveDistrict(pincode);
    if (!districtInfo) {
      return res.status(502).json({
        error: 'pincode_lookup_failed',
        message: 'Could not resolve district for this pincode. Check the pincode and try again.',
      });
    }

    const now = Date.now();
    const existing = await db.execute({
      sql: 'SELECT id, status FROM vet_applications WHERE user_id = ?',
      args: [req.userId],
    });

    let applicationId;
    if (existing.rows.length > 0) {
      applicationId = existing.rows[0].id;
      if (existing.rows[0].status === 'approved') {
        return res.status(409).json({
          error: 'already_approved',
          message: 'Your application is already approved and can no longer be edited.',
        });
      }
      // Re-submitting (while pending or after rejection) puts it back in review.
      await db.execute({
        sql: `UPDATE vet_applications SET
            status = 'pending', full_name = ?, phone = ?, email = ?, qualification = ?,
            license_number = ?, experience_years = ?, clinic_name = ?, address = ?,
            village = ?, pincode = ?, district = ?, state = ?, service_areas = ?,
            review_note = NULL, reviewed_by = NULL, reviewed_at = NULL, updated_at = ?
          WHERE id = ?`,
        args: [
          fullName, user.phone, user.email, qualification, licenseNumber, experienceYears,
          clinicName, address, village, pincode, districtInfo.district, districtInfo.state,
          JSON.stringify(cleanedAreas), now, applicationId,
        ],
      });
    } else {
      applicationId = randomUUID();
      await db.execute({
        sql: `INSERT INTO vet_applications (
            id, user_id, status, full_name, phone, email, qualification, license_number,
            experience_years, clinic_name, address, village, pincode, district, state,
            service_areas, created_at, updated_at
          ) VALUES (?, ?, 'pending', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        args: [
          applicationId, req.userId, fullName, user.phone, user.email, qualification,
          licenseNumber, experienceYears, clinicName, address, village, pincode,
          districtInfo.district, districtInfo.state, JSON.stringify(cleanedAreas), now, now,
        ],
      });
    }

    // Keep the core user and vet profile in sync (single source of truth: DB).
    await db.execute({
      sql: 'UPDATE users SET name = ?, updated_at = ? WHERE id = ?',
      args: [fullName, now, req.userId],
    });

    const profileExisting = await db.execute({
      sql: 'SELECT user_id FROM pashu_vet_profiles WHERE user_id = ?',
      args: [req.userId],
    });
    if (profileExisting.rows.length > 0) {
      await db.execute({
        sql: 'UPDATE pashu_vet_profiles SET pincode = ?, service_areas = ?, updated_at = ? WHERE user_id = ?',
        args: [pincode, JSON.stringify(cleanedAreas), now, req.userId],
      });
    } else {
      await db.execute({
        sql: `INSERT INTO pashu_vet_profiles (user_id, pincode, service_areas, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?)`,
        args: [req.userId, pincode, JSON.stringify(cleanedAreas), now, now],
      });
    }

    const saved = await db.execute({ sql: 'SELECT * FROM vet_applications WHERE id = ?', args: [applicationId] });
    return res.status(200).json({ application: saved.rows[0] });
  } catch (error) {
    console.error('[pashu-health/vet-applications] save failed:', error);
    return res.status(500).json({ error: 'application_failed', message: 'Could not save the application.' });
  }
});

/**
 * GET /api/v1/pashu-health/vet-applications/me
 * The signed-in vet's application (or { application: null } if none yet).
 */
router.get('/me', async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'SELECT * FROM vet_applications WHERE user_id = ?',
      args: [req.userId],
    });
    return res.status(200).json({ application: result.rows[0] ?? null });
  } catch (error) {
    console.error('[pashu-health/vet-applications/me] lookup failed:', error);
    return res.status(500).json({ error: 'lookup_failed', message: 'Could not load the application.' });
  }
});

export default router;
