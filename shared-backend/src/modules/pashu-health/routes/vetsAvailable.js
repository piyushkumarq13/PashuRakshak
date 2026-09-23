import { Router } from 'express';
import { db } from '../../../db/client.js';

const router = Router();

/**
 * GET /api/v1/pashu-health/vets/available?area=
 * Returns vets (join users + pashu_vet_profiles where role='vet')
 * whose service_areas JSON contains the given area string.
 */
router.get('/', async (req, res) => {
  const area = req.query.area;

  if (!area || !area.trim()) {
    return res.status(400).json({
      error: 'invalid_query',
      message: 'Query parameter "area" is required.',
    });
  }

  try {
    const result = await db.execute({
      sql: `SELECT u.id, u.phone, u.name, u.email, u.preferred_language,
               v.pincode, v.service_areas
        FROM users u
        JOIN pashu_vet_profiles v ON u.id = v.user_id
        WHERE u.role = 'vet' AND v.service_areas LIKE ?`,
      args: [`%${area}%`],
    });

    return res.status(200).json({ vets: result.rows });
  } catch (error) {
    console.error('[pashu-health/vets/available] lookup failed:', error);
    return res.status(500).json({
      error: 'vets_lookup_failed',
      message: 'Could not look up available vets.',
    });
  }
});

export default router;
