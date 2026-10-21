import crypto from 'node:crypto';
import { randomUUID } from 'node:crypto';

/**
 * Hash-chained audit log. Every important action appends an entry whose hash
 * covers the previous entry's hash, so any tampering of an earlier row breaks
 * the chain. Each entry: hash = sha256(prevHash | actor | action | entityType
 * | entityId | JSON(payload) | createdAt).
 */
export async function appendAuditLog(db, { actor, action, entityType, entityId, payload }) {
  try {
    const prev = await db.execute(
      'SELECT hash FROM audit_log ORDER BY created_at DESC, rowid DESC LIMIT 1',
    );
    const prevHash = prev.rows.length > 0 ? String(prev.rows[0].hash) : 'GENESIS';
    const createdAt = Date.now();
    const hash = crypto
      .createHash('sha256')
      .update(
        [
          prevHash,
          actor ?? '',
          action,
          entityType,
          entityId ?? '',
          JSON.stringify(payload ?? {}),
          String(createdAt),
        ].join('|'),
      )
      .digest('hex');
    await db.execute({
      sql: `INSERT INTO audit_log (id, actor, action, entity_type, entity_id, payload, prev_hash, hash, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      args: [
        randomUUID(),
        actor ?? null,
        action,
        entityType,
        entityId ?? null,
        JSON.stringify(payload ?? {}),
        prevHash,
        hash,
        createdAt,
      ],
    });
    return hash;
  } catch (error) {
    console.error('[auditLog] append failed:', error?.message ?? error);
    return null;
  }
}
