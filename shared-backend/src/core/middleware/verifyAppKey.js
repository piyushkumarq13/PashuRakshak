import { db } from '../../db/client.js';

/**
 * Reads the X-App-Key header, validates it against the apps table,
 * and attaches req.appId (the matching app's id) for downstream handlers.
 */
export async function verifyAppKey(req, res, next) {
  const apiKey = req.get('X-App-Key');

  if (!apiKey) {
    return res.status(401).json({
      error: 'missing_app_key',
      message: 'Missing X-App-Key header. Send the API key issued at app registration.',
    });
  }

  try {
    const result = await db.execute({
      sql: 'SELECT id FROM apps WHERE api_key = ?',
      args: [apiKey],
    });

    const row = result.rows[0];
    if (!row) {
      return res.status(401).json({
        error: 'invalid_app_key',
        message: 'Invalid X-App-Key — no registered app matches this key.',
      });
    }

    req.appId = row.id;
    return next();
  } catch (error) {
    console.error('[verifyAppKey] lookup failed:', error);
    return res.status(500).json({
      error: 'app_key_check_failed',
      message: 'Could not validate the app key. Try again later.',
    });
  }
}
