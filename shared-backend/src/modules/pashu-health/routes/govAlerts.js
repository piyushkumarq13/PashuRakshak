import { Router } from 'express';
import { db } from '../../../db/client.js';

const router = Router();

/**
 * GET /api/v1/pashu-health/gov-alerts
 * Returns all government alerts. Protected by verifyAppKey (router-level).
 * For future dashboard use.
 */
router.get('/', async (_req, res) => {
  try {
    const result = await db.execute(
      'SELECT * FROM pashu_gov_alerts ORDER BY created_at DESC',
    );
    return res.status(200).json({ govAlerts: result.rows });
  } catch (error) {
    console.error('[pashu-health/gov-alerts] list failed:', error);
    return res.status(500).json({
      error: 'gov_alerts_list_failed',
      message: 'Could not list government alerts.',
    });
  }
});

export default router;
