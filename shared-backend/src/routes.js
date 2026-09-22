import { Router } from 'express';
import { verifyAppKey } from './core/middleware/verifyAppKey.js';
import coreRoutes from './core/routes/apps.js';
import devicesRoutes from './core/routes/devices.js';
import pashuHealthRoutes from './modules/pashu-health/routes/index.js';

const router = Router();

// Core platform routes (app-key system) — shared by every client app.
router.use('/core', coreRoutes);
router.use('/core', devicesRoutes);

// PashuRakshak health module — every request must present a valid X-App-Key.
router.use('/pashu-health', verifyAppKey, pashuHealthRoutes);

// ---------------------------------------------------------------------------
// Future feature modules are mounted here, each in its own folder under
// src/modules/<name>/ with its own router:
//
//   // import otherRoutes from './modules/other/routes.js';
//   // router.use('/other', verifyAppKey, otherRoutes);
//
// which produces paths like: /api/v1/other/...
// ---------------------------------------------------------------------------

export default router;
