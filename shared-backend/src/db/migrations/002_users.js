/**
 * Core users system — CORE table (no prefix).
 * Shared across all app modules.
 */
export default {
  version: 3,
  name: '002_users',
  async up(db) {
    await db.batch(
      [
        `CREATE TABLE users (
          id TEXT PRIMARY KEY,
          phone TEXT UNIQUE,
          firebase_uid TEXT UNIQUE,
          role TEXT CHECK (role IN ('farmer', 'vet', 'admin')),
          name TEXT,
          email TEXT,
          preferred_language TEXT DEFAULT 'hi',
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        )`,
      ],
      'write',
    );
  },
};
