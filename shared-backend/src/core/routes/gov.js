import { Router } from 'express';
import { db } from '../../db/client.js';
import { requireRole, verifySession } from '../middleware/verifySession.js';
import { getActiveClusters } from '../../modules/pashu-health/services/clusterDetection.js';

/**
 * Government / District Magistrate data access.
 *
 * Mounted at /api/v1/gov with X-App-Key (routes.js) and, inside the router,
 * a gov session token. Applications are scoped to the magistrate's district
 * (district `*` or `all` = every district); all other platform data is full.
 */

const router = Router();

router.use(verifySession, requireRole('gov'));

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value);
  return text === '' ? null : text;
}

/**
 * Loads the magistrate row so district changes (admin upsert) apply without
 * forcing a re-login — the session token alone can go stale.
 */
async function loadMagistrateDistrict(req) {
  if (req.magistrateId) {
    try {
      const row = await db.execute({
        sql: 'SELECT district FROM gov_magistrates WHERE id = ?',
        args: [req.magistrateId],
      });
      const district = row.rows[0]?.district;
      if (district !== undefined && district !== null) {
        req.session = { ...req.session, district };
      }
    } catch (error) {
      console.warn('[gov] magistrate district lookup failed, using session:', error?.message ?? error);
    }
  }
  return req.session;
}

/** Case/space-insensitive district key so "Central Delhi" matches "central delhi". */
function districtKey(value) {
  return String(value ?? '')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ');
}

/** True when the magistrate covers every district. */
function seesAllDistricts(session) {
  const d = districtKey(session.district);
  return d === '' || d === '*' || d === 'all' || d === 'every district';
}

/** District-scoped application filter for SQL args (or null = no district filter). */
function applicationDistrictFilter(session) {
  if (seesAllDistricts(session)) return null;
  return str(session.district);
}

/**
 * GET /api/v1/gov/overview — platform-wide counts + district-scoped
 * vet-application counts (so the list and the badges always agree).
 */
router.get('/overview', async (req, res) => {
  try {
    await loadMagistrateDistrict(req);
    const districtFilter = applicationDistrictFilter(req.session);
    const appCountSql = districtFilter
      ? `SELECT status, COUNT(*) AS c FROM vet_applications
         WHERE LOWER(TRIM(district)) = LOWER(TRIM(?)) GROUP BY status`
      : `SELECT status, COUNT(*) AS c FROM vet_applications GROUP BY status`;
    const appCountArgs = districtFilter ? [districtFilter] : [];

    const [farmers, vets, apps, reports, govAlerts, alerts] = await Promise.all([
      db.execute(`SELECT COUNT(*) AS c FROM users WHERE role = 'farmer'`),
      db.execute(`SELECT COUNT(*) AS c FROM users WHERE role = 'vet'`),
      db.execute({ sql: appCountSql, args: appCountArgs }),
      db.execute(`SELECT status, COUNT(*) AS c FROM pashu_symptom_reports GROUP BY status`),
      db.execute(`SELECT acknowledged, COUNT(*) AS c FROM pashu_gov_alerts GROUP BY acknowledged`),
      db.execute(`SELECT COUNT(*) AS c FROM pashu_alerts`),
    ]);

    const applicationCounts = { pending: 0, approved: 0, rejected: 0 };
    for (const row of apps.rows) applicationCounts[row.status] = Number(row.c);

    const reportCounts = {};
    for (const row of reports.rows) reportCounts[row.status] = Number(row.c);

    const govAlertCounts = { acknowledged: 0, unacknowledged: 0 };
    for (const row of govAlerts.rows) {
      if (Number(row.acknowledged) === 1) govAlertCounts.acknowledged += Number(row.c);
      else govAlertCounts.unacknowledged += Number(row.c);
    }

    const clusters = await getActiveClusters();

    return res.status(200).json({
      overview: {
        farmers: Number(farmers.rows[0]?.c ?? 0),
        vets: Number(vets.rows[0]?.c ?? 0),
        vetApplications: applicationCounts,
        reports: reportCounts,
        reportsTotal: Object.values(reportCounts).reduce((sum, n) => sum + n, 0),
        activeClusters: clusters.length,
        govAlerts: govAlertCounts,
        alertsTotal: Number(alerts.rows[0]?.c ?? 0),
        district: districtFilter ?? '*',
      },
    });
  } catch (error) {
    console.error('[gov/overview] failed:', error);
    return res.status(500).json({ error: 'overview_failed', message: 'Could not load the overview.' });
  }
});

/**
 * GET /api/v1/gov/vet-applications?status=pending|approved|rejected|all
 * Scoped to the signed-in magistrate's district (default: pending).
 * District match is case-insensitive; magistrate district `*` sees all.
 */
router.get('/vet-applications', async (req, res) => {
  const status = str(req.query.status) ?? 'pending';
  const allowed = ['pending', 'approved', 'rejected', 'all'];
  if (!allowed.includes(status)) {
    return res.status(400).json({ error: 'invalid_status', message: `status must be one of: ${allowed.join(', ')}.` });
  }

  try {
    await loadMagistrateDistrict(req);
    const districtFilter = applicationDistrictFilter(req.session);
    let sql;
    let args;

    if (districtFilter) {
      // Case-insensitive match without requiring identical casing in the DB.
      sql =
        status === 'all'
          ? `SELECT * FROM vet_applications
             WHERE LOWER(TRIM(district)) = LOWER(TRIM(?))
             ORDER BY created_at DESC`
          : `SELECT * FROM vet_applications
             WHERE LOWER(TRIM(district)) = LOWER(TRIM(?)) AND status = ?
             ORDER BY created_at DESC`;
      args = status === 'all' ? [districtFilter] : [districtFilter, status];
    } else {
      sql =
        status === 'all'
          ? `SELECT * FROM vet_applications ORDER BY created_at DESC`
          : `SELECT * FROM vet_applications WHERE status = ? ORDER BY created_at DESC`;
      args = status === 'all' ? [] : [status];
    }

    const result = await db.execute({ sql, args });
    return res.status(200).json({
      applications: result.rows,
      district: districtFilter ?? '*',
    });
  } catch (error) {
    console.error('[gov/vet-applications] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list applications.' });
  }
});

/** Shared approve/reject handler — district-scoped. */
async function reviewApplication(req, res, nextStatus) {
  const applicationId = str(req.params.id);
  if (!applicationId) {
    return res.status(400).json({ error: 'invalid_body', message: 'Application id is required.' });
  }
  const note = str(req.body?.note);

  try {
    await loadMagistrateDistrict(req);
    const result = await db.execute({ sql: 'SELECT * FROM vet_applications WHERE id = ?', args: [applicationId] });
    const application = result.rows[0];
    if (!application) {
      return res.status(404).json({ error: 'not_found', message: 'Application not found.' });
    }
    if (
      !seesAllDistricts(req.session) &&
      districtKey(application.district) !== districtKey(req.session.district)
    ) {
      return res.status(403).json({
        error: 'wrong_district',
        message: `This application belongs to ${application.district}, not your district (${req.session.district}).`,
      });
    }
    if (application.status === nextStatus) {
      return res.status(409).json({
        error: 'already_reviewed',
        message: `Application is already ${nextStatus}.`,
      });
    }

    const now = Date.now();
    await db.execute({
      sql: `UPDATE vet_applications SET status = ?, review_note = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ?
        WHERE id = ?`,
      args: [nextStatus, note, req.magistrateId, now, now, applicationId],
    });

    const updated = await db.execute({ sql: 'SELECT * FROM vet_applications WHERE id = ?', args: [applicationId] });
    return res.status(200).json({ success: true, application: updated.rows[0] });
  } catch (error) {
    console.error(`[gov/vet-applications] ${nextStatus} failed:`, error);
    return res.status(500).json({ error: 'review_failed', message: 'Could not update the application.' });
  }
}

/** POST /api/v1/gov/vet-applications/:id/approve  Body: { note? } */
router.post('/vet-applications/:id/approve', (req, res) => reviewApplication(req, res, 'approved'));

/** POST /api/v1/gov/vet-applications/:id/reject  Body: { note } (note recommended) */
router.post('/vet-applications/:id/reject', (req, res) => reviewApplication(req, res, 'rejected'));

/**
 * GET /api/v1/gov/reports — every symptom report with animal species joined
 * so the portal table has a real animal column.
 */
router.get('/reports', async (_req, res) => {
  try {
    const result = await db.execute(`
      SELECT p.*,
             a.species AS animal_species,
             a.name AS animal_name,
             a.qr_code_id AS animal_qr_code_id
      FROM pashu_symptom_reports p
      LEFT JOIN pashu_animals a ON a.id = p.animal_id
      ORDER BY p.created_at DESC`);
    return res.status(200).json({ reports: result.rows });
  } catch (error) {
    console.error('[gov/reports] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list reports.' });
  }
});

/** GET /api/v1/gov/clusters — active outbreak clusters. */
router.get('/clusters', async (_req, res) => {
  try {
    const clusters = await getActiveClusters();
    return res.status(200).json({ clusters });
  } catch (error) {
    console.error('[gov/clusters] failed:', error);
    return res.status(500).json({ error: 'clusters_failed', message: 'Could not compute clusters.' });
  }
});

/** GET /api/v1/gov/gov-alerts — government alerts from risky field visits. */
router.get('/gov-alerts', async (_req, res) => {
  try {
    const result = await db.execute('SELECT * FROM pashu_gov_alerts ORDER BY created_at DESC');
    return res.status(200).json({ govAlerts: result.rows });
  } catch (error) {
    console.error('[gov/gov-alerts] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list alerts.' });
  }
});

/** POST /api/v1/gov/gov-alerts/:id/acknowledge */
router.post('/gov-alerts/:id/acknowledge', async (req, res) => {
  try {
    const result = await db.execute({ sql: 'SELECT * FROM pashu_gov_alerts WHERE id = ?', args: [req.params.id] });
    const alert = result.rows[0];
    if (!alert) {
      return res.status(404).json({ error: 'not_found', message: 'Alert not found.' });
    }
    await db.execute({ sql: 'UPDATE pashu_gov_alerts SET acknowledged = 1 WHERE id = ?', args: [req.params.id] });
    const updated = await db.execute({ sql: 'SELECT * FROM pashu_gov_alerts WHERE id = ?', args: [req.params.id] });
    return res.status(200).json({ success: true, govAlert: updated.rows[0] });
  } catch (error) {
    console.error('[gov/gov-alerts] acknowledge failed:', error);
    return res.status(500).json({ error: 'acknowledge_failed', message: 'Could not acknowledge the alert.' });
  }
});

/** GET /api/v1/gov/users — farmers & vets listing (profiles joined). */
router.get('/users', async (_req, res) => {
  try {
    const farmers = await db.execute(`
      SELECT u.id, u.phone, u.name, u.email, u.preferred_language, u.created_at,
             p.animal_count, p.village, p.pincode
      FROM users u
      LEFT JOIN pashu_farmer_profiles p ON p.user_id = u.id
      WHERE u.role = 'farmer'
      ORDER BY u.created_at DESC`);
    const vets = await db.execute(`
      SELECT u.id, u.phone, u.name, u.email, u.created_at,
             v.pincode, v.service_areas, a.status AS application_status, a.district
      FROM users u
      LEFT JOIN pashu_vet_profiles v ON v.user_id = u.id
      LEFT JOIN vet_applications a ON a.user_id = u.id
      WHERE u.role = 'vet'
      ORDER BY u.created_at DESC`);
    return res.status(200).json({ farmers: farmers.rows, vets: vets.rows });
  } catch (error) {
    console.error('[gov/users] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list users.' });
  }
});

export default router;
