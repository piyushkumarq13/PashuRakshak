import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../../db/client.js';

const router = Router();

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value);
  return text === '' ? null : text;
}

function num(value, fallback = null) {
  if (value === undefined || value === null || value === '') return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

/** POST /api/v1/pashu-health/vaccinations — create or upsert by id. */
router.post('/', async (req, res) => {
  const body = req.body ?? {};
  const id = str(body.id) ?? randomUUID();
  const animalId = str(body.animalId) ?? str(body.animal_id);
  const vaccineName = str(body.vaccineName) ?? str(body.vaccine_name);
  const dateGiven = num(body.dateGiven ?? body.date_given);
  const nextDue = num(body.nextDue ?? body.next_due);

  if (!animalId || !vaccineName || dateGiven === null || nextDue === null) {
    return res.status(400).json({
      error: 'invalid_body',
      message:
        'Fields "animalId", "vaccineName", "dateGiven", and "nextDue" are required (numbers for dates).',
    });
  }

  try {
    await db.execute({
      sql: `INSERT INTO pashu_vaccinations (id, animal_id, vaccine_name, date_given, next_due)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          animal_id = excluded.animal_id,
          vaccine_name = excluded.vaccine_name,
          date_given = excluded.date_given,
          next_due = excluded.next_due`,
      args: [id, animalId, vaccineName, dateGiven, nextDue],
    });
    const created = await db.execute({
      sql: 'SELECT * FROM pashu_vaccinations WHERE id = ?',
      args: [id],
    });
    return res.status(200).json({ success: true, vaccination: created.rows[0] });
  } catch (error) {
    console.error('[pashu-health/vaccinations] save failed:', error);
    return res.status(500).json({
      error: 'save_failed',
      message: error?.message ?? 'Could not save the vaccination.',
    });
  }
});

/** GET /api/v1/pashu-health/vaccinations */
router.get('/', async (_req, res) => {
  try {
    const result = await db.execute(
      'SELECT * FROM pashu_vaccinations ORDER BY date_given DESC',
    );
    return res.status(200).json({ vaccinations: result.rows });
  } catch (error) {
    console.error('[pashu-health/vaccinations] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list vaccinations.' });
  }
});

/** GET /api/v1/pashu-health/vaccinations/:id */
router.get('/:id', async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'SELECT * FROM pashu_vaccinations WHERE id = ?',
      args: [req.params.id],
    });
    const row = result.rows[0];
    if (!row) {
      return res.status(404).json({ error: 'not_found', message: 'Vaccination not found.' });
    }
    return res.status(200).json({ vaccination: row });
  } catch (error) {
    console.error('[pashu-health/vaccinations] get failed:', error);
    return res.status(500).json({ error: 'get_failed', message: 'Could not load the vaccination.' });
  }
});

/** DELETE /api/v1/pashu-health/vaccinations/:id */
router.delete('/:id', async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'DELETE FROM pashu_vaccinations WHERE id = ?',
      args: [req.params.id],
    });
    if (result.rowsAffected === 0) {
      return res.status(404).json({ error: 'not_found', message: 'Vaccination not found.' });
    }
    return res.status(200).json({ success: true });
  } catch (error) {
    console.error('[pashu-health/vaccinations] delete failed:', error);
    return res
      .status(500)
      .json({ error: 'delete_failed', message: 'Could not delete the vaccination.' });
  }
});

export default router;
