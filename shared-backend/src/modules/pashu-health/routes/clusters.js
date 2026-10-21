import { Router } from 'express';
import { getActiveClusters } from '../services/clusterDetection.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';

const router = Router();

/** GET /api/v1/pashu-health/clusters — currently active clusters (last 7 days). */
router.get('/', verifySession, requireRole('farmer', 'vet', 'gov'), async (_req, res) => {
  try {
    const clusters = await getActiveClusters();
    return res.status(200).json({ clusters });
  } catch (error) {
    console.error('[pashu-health/clusters] failed:', error);
    return res.status(500).json({
      error: 'clusters_failed',
      message: 'Could not compute active clusters.',
    });
  }
});

export default router;
