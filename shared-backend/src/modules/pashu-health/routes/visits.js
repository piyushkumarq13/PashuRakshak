import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { db } from '../../../db/client.js';
import { verifyAppKey } from '../../../core/middleware/verifyAppKey.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';
import { appendAuditLog } from '../services/auditLog.js';
import { haversineKm } from '../services/clusterDetection.js';
import { verifyAndExtractId } from '../services/qrSign.js';
import { idempotencyReplay, idempotencyStore } from '../services/idempotency.js';

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
 * - UPDATE pashu_symptom_reports SET vet_assessment = ?, status = 'resolved'
 *   plus verified/resolved metadata.
 * - If assessment === 'risky': insert a pashu_gov_alerts row
 *   (severity='high', message with animal/village context).
 */
router.post('/', verifyAppKey, verifySession, requireRole('vet'), async (req, res) => {
  const body = req.body ?? {};
  const reportId = str(body.reportId);
  const scannedQrCodeId = str(body.scannedQrCodeId);
  const latitude = num(body.latitude);
  const longitude = num(body.longitude);
  const assessment = str(body.assessment);
  const resolutionNote = str(body.resolutionNote) ?? str(body.resolution_note);

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

    // Replays of an offline push with the same Idempotency-Key return the
    // original response instead of creating a second visit.
    if (await idempotencyReplay(db, req, res)) return;

    // Look up the report to get animal_id, assigned_vet_id, village, and status
    const reportResult = await db.execute({
      sql: `SELECT p.symptoms, a.qr_code_id AS animal_qr_code_id, p.qr_code_id AS report_qr_code_id,
        a.species, p.village, p.assigned_vet_id, p.status, p.latitude AS report_lat, p.longitude AS report_lon, p.created_at
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

    // Verify the scanned QR's HMAC signature first (proof the payload wasn't
    // guessed or forged), then compare its embedded id to this report's QR.
    // Fallback to the animal's QR only for legacy rows.
    const expectedQrCodeId = reportRow.report_qr_code_id || reportRow.animal_qr_code_id;
    const signedId = verifyAndExtractId(scannedQrCodeId ?? '', req.appApiKey);
    const matched = signedId && expectedQrCodeId && signedId === expectedQrCodeId ? 1 : 0;

    const now = Date.now();
    const visitId = randomUUID();

    // Confidence: QR match is the base; subtract for GPS distance from the
    // reported location, and for suspiciously fast verification. Low-confidence
    // visits are FLAGGED, not hard-rejected (GPS is noisy).
    let distanceKm = null;
    if (reportRow.report_lat != null && reportRow.report_lon != null && latitude != null && longitude != null) {
      distanceKm = haversineKm(reportRow.report_lat, reportRow.report_lon, latitude, longitude);
    }
    const secondsSinceReport = reportRow.created_at ? (now - reportRow.created_at) / 1000 : null;
    const flags = [];
    if (!matched) flags.push('qr_mismatch');
    if (!signedId) flags.push('invalid_qr_signature');
    if (distanceKm != null && distanceKm > 1) flags.push('gps_far_from_report');
    if (secondsSinceReport != null && secondsSinceReport < 60) flags.push('verified_too_quickly');
    let confidence = 100;
    if (!matched) confidence -= 50;
    if (!signedId) confidence -= 20;
    if (distanceKm != null) confidence -= Math.min(Math.round(distanceKm * 10), 30);
    if (flags.includes('verified_too_quickly')) confidence -= 15;
    confidence = Math.max(0, Math.min(100, confidence));
    const flagged = confidence < 60 || flags.length > 0 ? 1 : 0;

    // Insert visit_verifications row (audit trail even when it doesn't match)
    await db.execute({
      sql: `INSERT INTO pashu_visit_verifications (id, report_id, vet_id, scanned_qr_code_id, matched, latitude, longitude, verified_at, confidence, flagged)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      args: [visitId, reportId, vetUserId, scannedQrCodeId, matched, latitude, longitude, now, confidence, flagged],
    });

    // A QR that does not match this report must NOT confirm the visit.
    if (!matched) {
      await appendAuditLog(db, {
        actor: vetUserId,
        action: 'visit_rejected_qr_mismatch',
        entityType: 'report',
        entityId: reportId,
        payload: { matched, confidence, flags },
      });
      return res.status(409).json({ error: "qr_mismatch", message: "Scanned QR does not match this animal" });
    }

    // On a match only: set vet_assessment and close the case. A successful
    // verified visit is a completed case, so it moves out of the active queue
    // into the vet's resolved list. The resolution metadata keeps the audit
    // trail separate from the clinical assessment.
    await db.execute({
      sql: `UPDATE pashu_symptom_reports
        SET vet_assessment = ?, status = 'resolved', qr_verified = 1,
            verified_at = ?, verified_by = ?, resolved_at = ?,
            resolved_by = ?, resolution_note = ?
        WHERE id = ?`,
      args: [assessment, now, vetUserId, now, vetUserId, resolutionNote, reportId],
    });

    await appendAuditLog(db, {
      actor: vetUserId,
      action: 'visit_verified',
      entityType: 'report',
      entityId: reportId,
      payload: { assessment, matched, confidence, flags },
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

    const responseBody = {
      success: true,
      visit: {
        id: visitId,
        reportId,
        matched,
        assessment,
        confidence,
        flagged,
        status: 'resolved',
      },
    };
    await idempotencyStore(db, req, responseBody);
    return res.status(200).json(responseBody);
  } catch (error) {
    console.error('[pashu-health/visits] upsert failed:', error);
    return res.status(500).json({
      error: 'visit_failed',
      message: 'Could not record the visit verification.',
    });
  }
});

export default router;
