import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { db } from '../../../db/client.js';
import { requireRole, verifySession } from '../../../core/middleware/verifySession.js';

const router = Router();

function str(value) {
  if (value === undefined || value === null) return null;
  const text = String(value).trim();
  if (text === '' || text.toLowerCase() === 'null' || text.toLowerCase() === 'undefined') {
    return null;
  }
  return text;
}

function num(value, fallback = null) {
  if (value === undefined || value === null || value === '') return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function isUniqueViolation(error) {
  return /UNIQUE constraint failed/i.test(error?.message ?? '');
}

/** POST /api/v1/pashu-health/animals — create or upsert by id. */
router.post('/', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  const body = req.body ?? {};
  const id = str(body.id) ?? randomUUID();
  const bodyOwnerId = str(body.ownerFarmerId) ?? str(body.owner_farmer_id);
  const ownerFarmerId = req.role === 'vet' ? bodyOwnerId : req.userId;
  const species = str(body.species);
  const name = str(body.name);
  const qrCodeId = str(body.qrCodeId) ?? str(body.qr_code_id);
  const createdAt = num(body.createdAt ?? body.created_at, Date.now());

  if (!ownerFarmerId || !species || !name || !qrCodeId) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Fields "ownerFarmerId", "species", "name", and "qrCodeId" are required.',
    });
  }

  if (!ownerFarmerId) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'A vet session must provide "ownerFarmerId".',
    });
  }

  try {
    const existing = await db.execute({
      sql: 'SELECT owner_farmer_id FROM pashu_animals WHERE id = ?',
      args: [id],
    });
    if (
      existing.rows.length > 0 &&
      req.role !== 'vet' &&
      existing.rows[0].owner_farmer_id !== req.userId
    ) {
      return res.status(403).json({
        error: 'forbidden_animal',
        message: 'This animal belongs to another farmer.',
      });
    }
    await db.execute({
      sql: `INSERT INTO pashu_animals (id, owner_farmer_id, species, name, qr_code_id, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          owner_farmer_id = excluded.owner_farmer_id,
          species = excluded.species,
          name = excluded.name,
          qr_code_id = excluded.qr_code_id,
          created_at = excluded.created_at`,
      args: [id, ownerFarmerId, species, name, qrCodeId, createdAt],
    });
    const created = await db.execute({
      sql: 'SELECT * FROM pashu_animals WHERE id = ?',
      args: [id],
    });
    return res.status(200).json({ success: true, animal: created.rows[0] });
  } catch (error) {
    if (isUniqueViolation(error)) {
      return res.status(409).json({
        error: 'qr_code_taken',
        message: 'An animal with this qrCodeId already exists.',
      });
    }
    console.error('[pashu-health/animals] save failed:', error);
    return res.status(500).json({ error: 'save_failed', message: 'Could not save the animal.' });
  }
});

/** GET /api/v1/pashu-health/animals — farmers see own, vets see all. */
router.get('/', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  try {
    const result = req.role === 'vet'
      ? await db.execute('SELECT * FROM pashu_animals ORDER BY created_at DESC')
      : await db.execute({
        sql: 'SELECT * FROM pashu_animals WHERE owner_farmer_id = ? ORDER BY created_at DESC',
        args: [req.userId],
      });
    return res.status(200).json({ animals: result.rows });
  } catch (error) {
    console.error('[pashu-health/animals] list failed:', error);
    return res.status(500).json({ error: 'list_failed', message: 'Could not list animals.' });
  }
});

/** GET /api/v1/pashu-health/animals/:id */
router.get('/:id', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'SELECT * FROM pashu_animals WHERE id = ?',
      args: [req.params.id],
    });
    const row = result.rows[0];
    if (
      !row ||
      (req.role !== 'vet' && row.owner_farmer_id !== req.userId)
    ) {
      return res.status(404).json({ error: 'not_found', message: 'Animal not found.' });
    }
    return res.status(200).json({ animal: row });
  } catch (error) {
    console.error('[pashu-health/animals] get failed:', error);
    return res.status(500).json({ error: 'get_failed', message: 'Could not load the animal.' });
  }
});

/** PUT /api/v1/pashu-health/animals/:id */
router.put('/:id', verifySession, requireRole('farmer', 'vet'), async (req, res) => {
  const body = req.body ?? {};
  const id = req.params.id;
  const bodyOwnerId = str(body.ownerFarmerId) ?? str(body.owner_farmer_id);
  const ownerFarmerId = req.role === 'vet' ? bodyOwnerId : req.userId;
  const species = str(body.species);
  const name = str(body.name);
  const qrCodeId = str(body.qrCodeId) ?? str(body.qr_code_id);
  const createdAt = num(body.createdAt ?? body.created_at);

  if (!ownerFarmerId || !species || !name || !qrCodeId) {
    return res.status(400).json({
      error: 'invalid_body',
      message: 'Fields "ownerFarmerId", "species", "name", and "qrCodeId" are required.',
    });
  }

  try {
    const existing = await db.execute({
      sql: 'SELECT id, owner_farmer_id FROM pashu_animals WHERE id = ?',
      args: [id],
    });
    if (
      existing.rows.length === 0 ||
      (req.role !== 'vet' && existing.rows[0].owner_farmer_id !== req.userId)
    ) {
      return res.status(404).json({ error: 'not_found', message: 'Animal not found.' });
    }

    await db.execute({
      sql: `UPDATE pashu_animals SET
          owner_farmer_id = ?, species = ?, name = ?, qr_code_id = ?,
          created_at = COALESCE(?, created_at)
        WHERE id = ?`,
      args: [ownerFarmerId, species, name, qrCodeId, createdAt, id],
    });
    const updated = await db.execute({
      sql: 'SELECT * FROM pashu_animals WHERE id = ?',
      args: [id],
    });
    return res.status(200).json({ success: true, animal: updated.rows[0] });
  } catch (error) {
    if (isUniqueViolation(error)) {
      return res.status(409).json({
        error: 'qr_code_taken',
        message: 'An animal with this qrCodeId already exists.',
      });
    }
    console.error('[pashu-health/animals] update failed:', error);
    return res.status(500).json({ error: 'update_failed', message: 'Could not update the animal.' });
  }
});

/** DELETE /api/v1/pashu-health/animals/:id */
router.delete('/:id', verifySession, requireRole('farmer'), async (req, res) => {
  try {
    const result = await db.execute({
      sql: 'DELETE FROM pashu_animals WHERE id = ? AND owner_farmer_id = ?',
      args: [req.params.id, req.userId],
    });
    if (result.rowsAffected === 0) {
      return res.status(404).json({ error: 'not_found', message: 'Animal not found.' });
    }
    return res.status(200).json({ success: true });
  } catch (error) {
    console.error('[pashu-health/animals] delete failed:', error);
    return res.status(500).json({ error: 'delete_failed', message: 'Could not delete the animal.' });
  }
});

export default router;
