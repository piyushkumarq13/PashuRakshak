import { Router } from 'express';
import { db } from '../../db/client.js';
import { requireRole, verifySession } from '../middleware/verifySession.js';
import { getActiveClusters } from '../../modules/pashu-health/services/clusterDetection.js';

/**
 * Government / District Magistrate data access.
 *
 * Mounted at /api/v1/gov with X-App-Key (routes.js) and, inside the router,
 * a gov session token. Applications are scoped to the magistrate's district;
 * all other platform data is available in full.
 */

const router = Router();

router.use(verifySession, requireRole('gov'));

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value);
  return text === '' ? null : text;
}

/** GET /api/v1/gov/overview — platform-wide counts for the dashboard. */
router.get('/overview', async (_req, res) => {
  try {
    const [farmers, vets, apps, reports, govAlerts, alerts] = await Promise.all([
      db.execute(`SELECT COUNT(*) AS c FROM users WHERE role = 'farmer'`),
      db.execute(`SELECT COUNT(*) AS c FROM users WHERE role = 'vet'`),
      db.execute(`SELECT status, COUNT(*) AS c FROM vet_applications GROUP BY status`),
      db.execute(`SELECT status, COUNT(*) AS c FROM pashu_symptom_reports GROUP BY status`),
      db.execute(`SELECT acknowledged, COUNT(*) AS c FROM pashu_gov_alerts GROUP BY acknowledged`),
      db.execute(`SELECT COUNT(*) AS c FROM pashu_alerts`),
    ]);

    const applicationCounts = { pending: 0, approved: 0, rejected: 0 };
    for (const row of apps.rows) applicationCounts[row.status] = Number(row.c);

    const reportCounts = {};
    for (const row of reports.rows) reportCounts[row.status] = Number(row.c);

    let govAlertCounts = { acknowledged: 0, unacknowledged: 0 };
    for (const row of govAlerts.rows) {
      if (Number(row.acknowledged) === 1) govAlertCounts.acknowledged = Number(row.c);
      else govAlertCounts.unacknowledged = Number(row.c);
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
 */
router.get('/vet-applications', async (req, res) => {
  const status = str(req.query.status) ?? 'pending';
  const allowed = ['pending', 'approved', 'rejected', 'all'];
  if (!allowed.includes(status)) {
    return res.status(400).json({ error: 'invalid_status', message: `status must be one of: ${allowed.join(', ')}.` });
  }

  try {
    const district = req.session.district;
    const sql =
      status === 'all'
        ? `SELECT * FROM vet_applications WHERE district = ? ORDER BY created_at DESC`
        : `SELECT * FROM vet_applications WHERE district = ? AND status = ? ORDER BY created_at DESC`;
    const args = status === 'all' ? [district] : [district, status];

    const result = await db.execute({ sql, args });
    return res.status(200).json({ applications: result.rows, district });
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
    const result = await db.execute({ sql: 'SELECT * FROM vet_applications WHERE id = ?', args: [applicationId] });
    const application = result.rows[0];
    if (!application) {
      return res.status(404).json({ error: 'not_found', message: 'Application not found.' });
    }
    if (application.district !== req.session.district) {
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

/** GET /api/v1/gov/reports — every symptom report (full data access). */
router.get('/reports', async (_req, res) => {
  try {
    const result = await db.execute('SELECT * FROM pashu_symptom_reports ORDER BY created_at DESC');
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
