import { randomBytes, randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../db/client.js';
import { adminSecret } from '../middleware/adminSecret.js';

const router = Router();

/**
 * POST /api/v1/core/apps
 * Registers a new client app. Admin-only (X-Admin-Secret) because apps
 * don't exist yet when registering the first one.
 */
router.post('/apps', adminSecret, async (req, res) => {
  const name = typeof req.body?.name === 'string' ? req.body.name.trim() : '';
  if (!name) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Body must include a non-empty "name" string.',
    });
  }

  try {
    const existing = await db.execute({
      sql: 'SELECT id FROM apps WHERE name = ?',
      args: [name],
    });
    if (existing.rows.length > 0) {
      return res.status(409).json({
        error: 'app_name_taken',
        message: `An app named "${name}" is already registered.`,
      });
    }

    const id = randomUUID();
    const apiKey = randomBytes(32).toString('hex');
    const createdAt = Date.now();

    await db.execute({
      sql: 'INSERT INTO apps (id, name, api_key, created_at) VALUES (?, ?, ?, ?)',
      args: [id, name, apiKey, createdAt],
    });

    return res.status(201).json({
      id,
      name,
      apiKey,
      message:
        'Store this API key now — it is shown ONCE here and will never be returned again.',
      keyShownOnce: true,
      createdAt,
    });
  } catch (error) {
    console.error('[apps] registration failed:', error);
    return res.status(500).json({
      error: 'registration_failed',
      message: 'Could not register the app. Try again later.',
    });
  }
});

/**
 * GET /api/v1/core/apps
 * Admin-only list of registered apps. Never returns api_key.
 */
router.get('/apps', adminSecret, async (_req, res) => {
  try {
    const result = await db.execute(
      'SELECT id, name, created_at FROM apps ORDER BY created_at ASC',
    );
    return res.status(200).json({ apps: result.rows });
  } catch (error) {
    console.error('[apps] list failed:', error);
    return res.status(500).json({
      error: 'list_failed',
      message: 'Could not list apps. Try again later.',
    });
  }
});

export default router;
