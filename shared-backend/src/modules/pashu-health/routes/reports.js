import { Router } from 'express';
import { db } from '../../../db/client.js';
import { verifyFirebaseToken } from '../../../core/middleware/verifyFirebaseToken.js';
import { detectClusterForReport, categorizeAndAssign } from '../services/clusterDetection.js';
import { generateAdvisory } from '../services/aiAdvisory.js';

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
 * Requires an app key (router-level verifyAppKey) AND a Firebase ID token —
 * report submissions must be tied to an authenticated farmer (req.uid).
 *
 * Accepts the SymptomReport push payload (camelCase, with symptoms/riskBreakdown
 * as JSON strings; snake_case aliases from ReportPushApi.buildPayload also work).
 */
router.post('/', verifyFirebaseToken, async (req, res) => {
  const body = req.body ?? {};

  const id = str(body.id);
  if (!id) {
    return res.status(400).json({ error: 'invalid_body', message: 'Field "id" is required.' });
  }

  const animalId = str(body.animalId) ?? str(body.animal_id);
  const farmerId = str(body.farmerId) ?? str(body.farmer_id);
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

  try {
    // Farm animal may not exist on the server yet (app pushes animals separately).
    // Create a placeholder so FK does not reject the report.
    const animalRow = await db.execute({
      sql: 'SELECT id, species FROM pashu_animals WHERE id = ?',
      args: [animalId],
    });
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
           latitude, longitude, risk_score, risk_breakdown, status, synced, created_at, village
         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
           status = excluded.status,
           synced = excluded.synced,
           created_at = excluded.created_at,
           village = COALESCE(excluded.village, pashu_symptom_reports.village)`,
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
            sql: 'SELECT ai_risk_category, symptoms, village, risk_score, risk_breakdown FROM pashu_symptom_reports WHERE id = ?',
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

     return res.status(200).json({ success: true, aiAdvisory });
  } catch (error) {
    console.error('[pashu-health/reports] upsert failed:', error);
    return res.status(500).json({
      error: 'report_upsert_failed',
      message: error?.message ?? 'Could not save the report.',
    });
  }
});

/** GET /api/v1/pashu-health/reports */
router.get('/', async (_req, res) => {
  try {
    const result = await db.execute(
      'SELECT * FROM pashu_symptom_reports ORDER BY created_at DESC',
    );
    return res.status(200).json({ reports: result.rows });
  } catch (error) {
    console.error('[pashu-health/reports] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list reports.' });
  }
});

/** GET /api/v1/pashu-health/reports/:id */
router.get('/:id', async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'SELECT * FROM pashu_symptom_reports WHERE id = ?',
      args: [req.params.id],
    });
    const row = result.rows[0];
    if (!row) {
      return res.status(404).json({ error: 'not_found', message: 'Report not found.' });
    }
    return res.status(200).json({ report: row });
  } catch (error) {
    console.error('[pashu-health/reports] get failed:', error);
    return res.status(500).json({ error: 'get_failed', message: 'Could not load the report.' });
  }
});

export default router;
