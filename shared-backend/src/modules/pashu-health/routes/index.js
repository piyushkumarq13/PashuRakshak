import { Router } from 'express';
import alerts from './alerts.js';
import animals from './animals.js';
import aiChat from './aiChat.js';
import clusters from './clusters.js';
import farmerProfiles from './farmerProfiles.js';
import govAlerts from './govAlerts.js';
import reports from './reports.js';
import vaccinations from './vaccinations.js';
import vetProfiles from './vetProfiles.js';
import vetsAvailable from './vetsAvailable.js';
import visits from './visits.js';

const router = Router();

router.use('/reports', reports);
router.use('/animals', animals);
router.use('/vaccinations', vaccinations);
router.use('/alerts', alerts);
router.use('/clusters', clusters);
router.use('/farmer-profiles', farmerProfiles);
router.use('/vet-profiles', vetProfiles);
router.use('/vets', vetsAvailable);
router.use('/reports', aiChat);
router.use('/visits', visits);
router.use('/gov-alerts', govAlerts);

export default router;
