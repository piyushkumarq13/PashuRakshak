/**
 * Trust-layer schema: hash-chained audit log, idempotency keys for offline
 * sync pushes, and QR-verification confidence/flag columns on visit rows.
 */
export default {
  version: 11,
  name: '010_trust_layer',
  async up(db) {
    await db.batch(
      [
        `CREATE TABLE IF NOT EXISTS audit_log (
          id TEXT PRIMARY KEY,
          actor TEXT,
          action TEXT NOT NULL,
          entity_type TEXT NOT NULL,
          entity_id TEXT,
          payload TEXT,
          prev_hash TEXT NOT NULL,
          hash TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )`,
        `CREATE TABLE IF NOT EXISTS idempotency_keys (
          key TEXT NOT NULL,
          endpoint TEXT NOT NULL,
          response_json TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          PRIMARY KEY (key, endpoint)
        )`,
        `ALTER TABLE pashu_visit_verifications ADD COLUMN confidence INTEGER`,
        `ALTER TABLE pashu_visit_verifications ADD COLUMN flagged INTEGER NOT NULL DEFAULT 0`,
      ],
      'write',
    );
  },
};
