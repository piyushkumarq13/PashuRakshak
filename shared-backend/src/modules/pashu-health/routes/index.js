import { Router } from 'express';
import alerts from './alerts.js';
import animals from './animals.js';
import clusters from './clusters.js';
import reports from './reports.js';
import vaccinations from './vaccinations.js';

const router = Router();

router.use('/reports', reports);
router.use('/animals', animals);
router.use('/vaccinations', vaccinations);
router.use('/alerts', alerts);
router.use('/clusters', clusters);

export default router;
