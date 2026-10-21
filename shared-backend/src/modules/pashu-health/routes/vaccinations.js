import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../../db/client.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';

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
router.post('/', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
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
    const animalRow = await db.execute({
      sql: 'SELECT id, owner_farmer_id FROM pashu_animals WHERE id = ?',
      args: [animalId],
    });
    if (animalRow.rows.length === 0) {
      return res.status(409).json({
        error: 'animal_not_found',
        message: 'Create the animal before recording a vaccination.',
      });
    }
    if (req.role !== 'vet' && animalRow.rows[0].owner_farmer_id !== req.userId) {
      return res.status(403).json({
        error: 'forbidden_animal',
        message: 'This animal belongs to another farmer.',
      });
    }
    if (req.role !== 'vet') {
      const existing = await db.execute({
        sql: `SELECT a.owner_farmer_id AS owner
              FROM pashu_vaccinations v
              JOIN pashu_animals a ON a.id = v.animal_id
              WHERE v.id = ?`,
        args: [id],
      });
      if (existing.rows.length > 0 && existing.rows[0].owner !== req.userId) {
        return res.status(403).json({
          error: 'forbidden_vaccination',
          message: 'This vaccination belongs to another farmer.',
        });
      }
    }
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

/** GET /api/v1/pashu-health/vaccinations — farmers see own animals, vets see all. */
router.get('/', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  try {
    const result = req.role === 'vet'
      ? await db.execute('SELECT * FROM pashu_vaccinations ORDER BY date_given DESC')
      : await db.execute({
        sql: `SELECT v.* FROM pashu_vaccinations v
              JOIN pashu_animals a ON a.id = v.animal_id
              WHERE a.owner_farmer_id = ?
              ORDER BY v.date_given DESC`,
        args: [req.userId],
      });
    return res.status(200).json({ vaccinations: result.rows });
  } catch (error) {
    console.error('[pashu-health/vaccinations] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list vaccinations.' });
  }
});

/** GET /api/v1/pashu-health/vaccinations/:id */
router.get('/:id', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  try {
    const result = await db.execute({
      sql: `SELECT v.*, a.owner_farmer_id AS owner_farmer_id
            FROM pashu_vaccinations v
            JOIN pashu_animals a ON a.id = v.animal_id
            WHERE v.id = ?`,
      args: [req.params.id],
    });
    const row = result.rows[0];
    if (
      !row ||
      (req.role !== 'vet' && row.owner_farmer_id !== req.userId)
    ) {
      return res.status(404).json({ error: 'not_found', message: 'Vaccination not found.' });
    }
    return res.status(200).json({ vaccination: row });
  } catch (error) {
    console.error('[pashu-health/vaccinations] get failed:', error);
    return res.status(500).json({ error: 'get_failed', message: 'Could not load the vaccination.' });
  }
});

/** DELETE /api/v1/pashu-health/vaccinations/:id */
router.delete('/:id', verifySession, requireRole('farmer'), async (req, res) => {
  try {
    const result = await db.execute({
      sql: `DELETE FROM pashu_vaccinations
            WHERE id = ?
              AND animal_id IN (SELECT id FROM pashu_animals WHERE owner_farmer_id = ?)`,
      args: [req.params.id, req.userId],
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
