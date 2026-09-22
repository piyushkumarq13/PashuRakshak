/**
 * Core schema shared across every future app module.
 * Keep table names generic — no project-specific prefixes.
 */
export default {
  version: 1,
  name: '000_core',
  async up(db) {
    await db.batch(
      [
        `CREATE TABLE apps (
          id TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          api_key TEXT NOT NULL UNIQUE,
          created_at INTEGER NOT NULL
        )`,
        `CREATE TABLE device_tokens (
          id TEXT PRIMARY KEY,
          app_id TEXT NOT NULL REFERENCES apps(id),
          uid TEXT,
          role TEXT,
          fcm_token TEXT,
          updated_at INTEGER
        )`,
      ],
      'write',
    );
  },
};
