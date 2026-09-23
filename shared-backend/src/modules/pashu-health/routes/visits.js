import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { db } from '../../../db/client.js';
import { verifyAppKey } from '../../../core/middleware/verifyAppKey.js';
import { verifySession } from '../../../core/middleware/verifySession.js';

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
 * POST /api/v1/pashu-health/visits
 * Protected by verifyAppKey + verifySession.
 * Accepts { reportId, scannedQrCodeId, latitude, longitude, assessment [risky/moderate/mild] }.
 *
 * - Look up the report's animal_id → pashu_animals.qr_code_id, compare
 *   to scannedQrCodeId to set `matched`.
 * - Reject 403 if the session's user id doesn't match assigned_vet_id —
 *   unless assigned_vet_id is null (fallback path).
 * - Insert visit_verifications row.
 * - UPDATE pashu_symptom_reports SET vet_assessment = ?, status = 'examined'.
 * - If assessment === 'risky': insert a pashu_gov_alerts row
 *   (severity='high', message with animal/village context).
 */
router.post('/', verifyAppKey, verifySession, async (req, res) => {
  const body = req.body ?? {};
  const reportId = str(body.reportId);
  const scannedQrCodeId = str(body.scannedQrCodeId);
  const latitude = num(body.latitude);
  const longitude = num(body.longitude);
  const assessment = str(body.assessment);

  if (!reportId || !assessment) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Fields "reportId" and "assessment" are required.',
    });
  }

  if (!['risky', 'moderate', 'mild'].includes(assessment)) {
    return res.status(400).json({
      error: 'invalid_body',
      message: '"assessment" must be one of: risky, moderate, mild.',
    });
  }

  const vetUserId = req.userId;

  try {
    if (!vetUserId) {
      return res.status(403).json({ error: 'forbidden', message: 'A vet session is required.' });
    }

    // Look up the report to get animal_id, assigned_vet_id, village, and status
    const reportResult = await db.execute({
      sql: `SELECT p.symptoms, a.qr_code_id, a.species, p.village, p.assigned_vet_id, p.status
        FROM pashu_symptom_reports p
        JOIN pashu_animals a ON p.animal_id = a.id
        WHERE p.id = ?`,
      args: [reportId],
    });

    if (reportResult.rows.length === 0) {
      return res.status(404).json({ error: 'not_found', message: 'Report not found.' });
    }

    const reportRow = reportResult.rows[0];
    const assignedVetId = reportRow.assigned_vet_id;

    // Reject 403 if vet doesn't match assigned_vet_id and assigned_vet_id is not null
    if (assignedVetId !== null && assignedVetId !== vetUserId) {
      return res.status(403).json({
        error: 'forbidden',
        message: 'This report is not assigned to you.',
      });
    }

    // Compare scanned QR code to the animal's QR code
    const animalQrCodeId = reportRow.qr_code_id;
    const matched = scannedQrCodeId === animalQrCodeId ? 1 : 0;

    const now = Date.now();
    const visitId = randomUUID();

    // Insert visit_verifications row
    await db.execute({
      sql: `INSERT INTO pashu_visit_verifications (id, report_id, vet_id, scanned_qr_code_id, matched, latitude, longitude, verified_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      args: [visitId, reportId, vetUserId, scannedQrCodeId, matched, latitude, longitude, now],
    });

    // UPDATE pashu_symptom_reports SET vet_assessment = ?, status = 'examined'
    await db.execute({
      sql: `UPDATE pashu_symptom_reports SET vet_assessment = ?, status = 'examined' WHERE id = ?`,
      args: [assessment, reportId],
    });

    // If assessment === 'risky', insert a pashu_gov_alerts row
    if (assessment === 'risky') {
      const alertId = randomUUID();
      const species = reportRow.species ?? 'unknown';
      const village = reportRow.village ?? 'unknown village';
      const message = `Gov alert: ${species} in ${village} flagged as risky — field visit verified by vet`;

      await db.execute({
        sql: `INSERT INTO pashu_gov_alerts (id, report_id, severity, message, acknowledged, created_at)
          VALUES (?, ?, 'high', ?, 0, ?)`,
        args: [alertId, reportId, message, now],
      });
    }

    return res.status(200).json({
      success: true,
      visit: {
        id: visitId,
        reportId,
        matched,
        assessment,
      },
    });
  } catch (error) {
    console.error('[pashu-health/visits] upsert failed:', error);
    return res.status(500).json({
      error: 'visit_failed',
      message: 'Could not record the visit verification.',
    });
  }
});

export default router;
