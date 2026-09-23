import { Router } from 'express';
import { verifyAppKey } from './core/middleware/verifyAppKey.js';
import adminMagistratesRoutes from './core/routes/adminMagistrates.js';
import authRoutes from './core/routes/auth.js';
import coreRoutes from './core/routes/apps.js';
import devicesRoutes from './core/routes/devices.js';
import govRoutes from './core/routes/gov.js';
import pincodeRoutes from './core/routes/pincode.js';
import usersRoutes from './core/routes/users.js';
import pashuHealthRoutes from './modules/pashu-health/routes/index.js';

const router = Router();

// Core platform routes (app-key system) — shared by every client app.
router.use('/core', coreRoutes);
router.use('/core', devicesRoutes);
router.use('/core', usersRoutes);
router.use('/core', pincodeRoutes);
router.use('/core', adminMagistratesRoutes);

// Custom auth (email OTP + PIN/password sessions) — no Firebase Auth.
router.use('/auth', verifyAppKey, authRoutes);

// Government / district-magistrate dashboard — app key + gov session
// (session + role checks applied inside the gov router).
router.use('/gov', verifyAppKey, govRoutes);

// PashuRakshak health module — every request must present a valid X-App-Key.
router.use('/pashu-health', verifyAppKey, pashuHealthRoutes);

export default router;
