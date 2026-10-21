import { Router } from 'express';
import { db } from '../../../db/client.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';
import { detectClusterForReport, categorizeAndAssign } from '../services/clusterDetection.js';
import { generateAdvisory } from '../services/aiAdvisory.js';
import { analyzeRisk } from '../services/aiRiskScore.js';
import { appendAuditLog } from '../services/auditLog.js';
import { idempotencyReplay, idempotencyStore } from '../services/idempotency.js';

const router = Router();

const REPORT_STATUSES = [
  'reported',
  'vet_assigned',
  'examined',
  'sample_sent',
  'confirmed',
  'resolved',
];

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

/** Accepts a JSON string (as the app sends) or an array/object; returns a JSON string. */
function jsonish(value, fallback) {
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed === '') return fallback;
    try {
      JSON.parse(trimmed);
      return trimmed;
    } catch {
      return fallback;
    }
  }
  if (Array.isArray(value) || (value && typeof value === 'object')) {
    return JSON.stringify(value);
  }
  return fallback;
}

/**
 * POST /api/v1/pashu-health/reports
 * Idempotent upsert by id — the Android app may retry the same report.
 * Requires an app key (router-level verifyAppKey) AND a session token —
 * report submissions are tied to the authenticated farmer (req.userId).
 *
 * Accepts the SymptomReport push payload (camelCase, with symptoms/riskBreakdown
 * as JSON strings; snake_case aliases from ReportPushApi.buildPayload also work).
 */
router.post('/', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  const body = req.body ?? {};

  // Replays of an offline push with the same Idempotency-Key return the
  // original JSON instead of duplicating the effect.
  if (await idempotencyReplay(db, req, res)) return;

  const id = str(body.id);
  if (!id) {
    return res.status(400).json({ error: 'invalid_body', message: 'Field "id" is required.' });
  }

  const isVet = req.role === 'vet';
  const animalId = str(body.animalId) ?? str(body.animal_id);
  const bodyFarmerId = str(body.farmerId) ?? str(body.farmer_id);
  // Farmers may only write reports for themselves. Vets may write a report on
  // behalf of a farmer (case workflow), but must name a real farmer.
  if (!isVet && bodyFarmerId && bodyFarmerId !== req.userId) {
    return res.status(403).json({
      error: 'forbidden_farmer',
      message: 'A farmer session cannot submit reports for another farmer.',
    });
  }
  const farmerId = isVet ? bodyFarmerId : req.userId;
  if (!animalId || !farmerId) {
    return res
      .status(400)
      .json({ error: 'invalid_body', message: 'Fields "animalId" and "farmerId" are required.' });
  }

  const latitude = num(body.latitude);
  const longitude = num(body.longitude);
  if (latitude === null || longitude === null) {
    return res
      .status(400)
      .json({ error: 'invalid_body', message: 'Fields "latitude" and "longitude" must be numbers.' });
  }

  const status = str(body.status) ?? 'reported';
  if (!REPORT_STATUSES.includes(status)) {
    return res.status(400).json({
      error: 'invalid_status',
      message: `"status" must be one of: ${REPORT_STATUSES.join(', ')}.`,
    });
  }

  const symptoms = jsonish(body.symptoms, '[]');
  const riskBreakdown = jsonish(body.riskBreakdown ?? body.risk_breakdown, '{}');
  const photoLocalPath = str(body.photoLocalPath) ?? str(body.photo_local_path) ?? '';
  const photoRemoteUrl = str(body.photoRemoteUrl) ?? str(body.photo_remote_url);
   const riskScore = num(body.riskScore ?? body.risk_score, 0);
   const createdAt = num(body.createdAt ?? body.created_at, Date.now());
   const synced = body.synced === false || body.synced === 0 ? 0 : 1;
    const village = str(body.village);
    // Vet-controlled columns are ignored for farmer sessions on INSERT as well
    // as UPDATE, so a farmer cannot fabricate an assigned vet or assessment.
    const assignedVetId = isVet
      ? str(body.assignedVetId) ?? str(body.assigned_vet_id)
      : null;
    const vetAssessment = isVet
      ? str(body.vetAssessment) ?? str(body.vet_assessment)
      : null;
    const qrCodeId = str(body.qrCodeId) ?? str(body.qr_code_id);
    const diseaseDescription = str(body.diseaseDescription) ?? str(body.disease_description);
    // qr_verified / verified_at / verified_by are never accepted from the body —
    // only routes/visits.js sets them.

    // Fraud signals (surfaced and audit-logged, never hard-rejected):
    //  - photo_remote_url already seen on a different report → reused photo
    //  - many reports from one farmer in a short window     → report spike
    const flags = [];
    try {
      if (photoRemoteUrl) {
        const dup = await db.execute({
          sql: 'SELECT COUNT(*) AS n FROM pashu_symptom_reports WHERE photo_remote_url = ? AND id != ?',
          args: [photoRemoteUrl, id],
        });
        if (Number(dup.rows[0]?.n ?? 0) > 0) flags.push('photo_reused');
      }
      const spike = await db.execute({
        sql: 'SELECT COUNT(*) AS n FROM pashu_symptom_reports WHERE farmer_id = ? AND created_at > ?',
        args: [farmerId, Date.now() - 600_000],
      });
      if (Number(spike.rows[0]?.n ?? 0) >= 5) flags.push('report_spike');
    } catch (error) {
      console.warn('[reports] fraud signal check failed:', error?.message ?? error);
    }

  try {
    if (!isVet) {
      const existingReport = await db.execute({
        sql: 'SELECT farmer_id FROM pashu_symptom_reports WHERE id = ?',
        args: [id],
      });
      if (
        existingReport.rows.length > 0 &&
        existingReport.rows[0].farmer_id !== req.userId
      ) {
        return res.status(403).json({
          error: 'forbidden_report',
          message: 'This report belongs to another farmer.',
        });
      }
    }

    // Farm animal may not exist on the server yet (app pushes animals separately).
    // Create a placeholder so FK does not reject the report.
    const animalRow = await db.execute({
      sql: 'SELECT id, species, owner_farmer_id FROM pashu_animals WHERE id = ?',
      args: [animalId],
    });
    if (
      !isVet &&
      animalRow.rows.length > 0 &&
      animalRow.rows[0].owner_farmer_id !== req.userId
    ) {
      return res.status(403).json({
        error: 'forbidden_animal',
        message: 'This animal belongs to another farmer.',
      });
    }
    if (animalRow.rows.length === 0) {
      await db.execute({
        sql: `INSERT OR IGNORE INTO pashu_animals
            (id, owner_farmer_id, species, name, qr_code_id, created_at)
          VALUES (?, ?, 'unknown', 'Unknown animal', ?, ?)`,
        args: [animalId, farmerId, `QR-PENDING-${animalId}`, Date.now()],
      });
    }

     await db.execute({
        sql: `INSERT INTO pashu_symptom_reports (
            id, animal_id, farmer_id, symptoms, photo_local_path, photo_remote_url,
            latitude, longitude, risk_score, risk_breakdown, status, synced, created_at, village, assigned_vet_id, vet_assessment, qr_code_id, disease_description
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT(id) DO UPDATE SET
            animal_id = excluded.animal_id,
            farmer_id = excluded.farmer_id,
            symptoms = excluded.symptoms,
            photo_local_path = CASE
              WHEN excluded.photo_local_path != '' THEN excluded.photo_local_path
              ELSE pashu_symptom_reports.photo_local_path END,
            photo_remote_url = COALESCE(excluded.photo_remote_url, pashu_symptom_reports.photo_remote_url),
            latitude = excluded.latitude,
            longitude = excluded.longitude,
            risk_score = excluded.risk_score,
             risk_breakdown = excluded.risk_breakdown,
           status = CASE WHEN ? THEN excluded.status ELSE pashu_symptom_reports.status END,
             synced = excluded.synced,
           created_at = excluded.created_at,
           village = COALESCE(excluded.village, pashu_symptom_reports.village),
            assigned_vet_id = CASE WHEN ? THEN excluded.assigned_vet_id ELSE pashu_symptom_reports.assigned_vet_id END,
            vet_assessment = CASE WHEN ? THEN excluded.vet_assessment ELSE pashu_symptom_reports.vet_assessment END,
            qr_code_id = COALESCE(excluded.qr_code_id, pashu_symptom_reports.qr_code_id),
            disease_description = COALESCE(excluded.disease_description, pashu_symptom_reports.disease_description)`,
       args: [
         id,
         animalId,
         farmerId,
         symptoms,
         photoLocalPath,
         photoRemoteUrl,
         latitude,
         longitude,
         riskScore,
         riskBreakdown,
         status,
         synced,
         createdAt,
          village,
          assignedVetId,
          vetAssessment,
          qrCodeId,
          diseaseDescription,
          // vet flags for the three UPDATE CASEs (status, assigned_vet_id, vet_assessment)
          isVet ? 1 : 0,
          isVet ? 1 : 0,
          isVet ? 1 : 0,
        ],
     });

      // Cluster detection + AI categorization/vet assignment run after a
      // successful save; they must never fail the report push itself.
      let aiAdvisory = null;
      try {
        const clusterResult = await detectClusterForReport(
          {
            id,
            latitude,
            longitude,
            status,
            createdAt,
          },
          req.appId,
        );

        await categorizeAndAssign(
          {
            id,
            riskScore,
            village,
            clustered: clusterResult.clustered,
          },
          req.appId,
        );

        // Look up the farmer's preferred language and generate advisory.
        const userRow = await db.execute({
          sql: 'SELECT preferred_language FROM users WHERE id = ?',
          args: [farmerId],
        });
        if (userRow.rows.length > 0) {
          const preferredLanguage = userRow.rows[0].preferred_language;
          const reportRow = await db.execute({
            sql: 'SELECT ai_risk_category, symptoms, village, risk_score, risk_breakdown, assigned_vet_id, vet_assessment FROM pashu_symptom_reports WHERE id = ?',
            args: [id],
          });
          aiAdvisory = await generateAdvisory(
            {
              id,
              species: animalRow.rows[0]?.species,
              symptoms: reportRow.rows[0]?.symptoms,
              riskScore: reportRow.rows[0]?.risk_score,
              riskBreakdown: reportRow.rows[0]?.risk_breakdown,
              village: reportRow.rows[0]?.village,
              ai_risk_category: reportRow.rows[0]?.ai_risk_category,
              farmerId,
            },
            preferredLanguage,
          );
        }
      } catch (error) {
        console.error('[pashu-health/reports] cluster detection failed:', error);
      }

      await appendAuditLog(db, {
        actor: farmerId,
        action: 'report_upserted',
        entityType: 'report',
        entityId: id,
        payload: { status, village, flags },
      });

      const responseBody = { success: true, aiAdvisory, flags };
      await idempotencyStore(db, req, responseBody);
      return res.status(200).json(responseBody);
  } catch (error) {
    console.error('[pashu-health/reports] upsert failed:', error);
    return res.status(500).json({
      error: 'report_upsert_failed',
      message: error?.message ?? 'Could not save the report.',
    });
  }
});

/**
 * POST /api/v1/pashu-health/reports/analyze-risk
 * Body: { symptoms: string[], diseaseDescription: string|null, species: string|null }.
 * Runs the AI disease analysis FIRST so the app can show the AI score before
 * the farmer confirms the report (client also combines it with its own
 * rule-based score). Returns 503 when the AI is unavailable so the app can
 * fall back to the rule-based score.
 */
router.post('/analyze-risk', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  const body = req.body ?? {};
  const symptoms = Array.isArray(body.symptoms) ? body.symptoms.map(String) : [];
  const diseaseDescription = str(body.diseaseDescription) ?? str(body.disease_description);
  const species = str(body.species);

  if (symptoms.length === 0 && !diseaseDescription) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Provide at least one symptom or a disease description.',
    });
  }

  const result = await analyzeRisk({ species, symptoms, diseaseDescription });
  if (!result) {
    return res.status(503).json({
      error: 'ai_unavailable',
      message: 'AI analysis is unavailable right now.',
    });
  }
  return res.status(200).json(result);
});

/** GET /api/v1/pashu-health/reports — farmers see own, vets see all. */
router.get('/', verifySession, async (req, res) => {
  try {
    const result = req.role === 'vet'
      ? await db.execute('SELECT * FROM pashu_symptom_reports ORDER BY created_at DESC')
      : await db.execute({
        sql: 'SELECT * FROM pashu_symptom_reports WHERE farmer_id = ? ORDER BY created_at DESC',
        args: [req.userId],
      });
    const reports = result.rows.map((row) => ({
      ...row,
      qrVerified: row.qr_verified === 1 || row.qr_verified === true,
      verifiedAt: row.verified_at ?? null,
    }));
    return res.status(200).json({ reports });
  } catch (error) {
    console.error('[pashu-health/reports] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list reports.' });
  }
});

/** GET /api/v1/pashu-health/reports/:id */
router.get('/:id', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'SELECT * FROM pashu_symptom_reports WHERE id = ?',
      args: [req.params.id],
    });
    const row = result.rows[0];
    if (!row) {
      return res.status(404).json({ error: 'not_found', message: 'Report not found.' });
    }
    if (req.role !== 'vet' && row.farmer_id !== req.userId) {
      return res.status(404).json({ error: 'not_found', message: 'Report not found.' });
    }
    return res.status(200).json({
      report: {
        ...row,
        qrVerified: row.qr_verified === 1 || row.qr_verified === true,
        verifiedAt: row.verified_at ?? null,
      },
    });
  } catch (error) {
    console.error('[pashu-health/reports] get failed:', error);
    return res.status(500).json({ error: 'get_failed', message: 'Could not load the report.' });
  }
});

export default router;
