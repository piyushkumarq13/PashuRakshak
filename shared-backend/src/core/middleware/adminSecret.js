import { env } from '../../config/env.js';

/**
 * Admin-only gate for core bootstrap routes (app registration/listing).
 * Apps do not exist yet when the first one is registered, so this cannot
 * go through verifyAppKey — it checks the X-Admin-Secret header instead.
 */
export function adminSecret(req, res, next) {
  const provided = req.get('X-Admin-Secret');

  if (!provided) {
    return res.status(401).json({
      error: 'missing_admin_secret',
      message: 'Missing X-Admin-Secret header.',
    });
  }

  if (provided !== env.adminSecret) {
    return res.status(401).json({
      error: 'invalid_admin_secret',
      message: 'Invalid X-Admin-Secret.',
    });
  }

  return next();
}
