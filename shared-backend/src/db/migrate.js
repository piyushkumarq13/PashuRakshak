import { readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { db } from './client.js';

const migrationsDir = path.join(path.dirname(fileURLToPath(import.meta.url)), 'migrations');

/**
 * Migration runner.
 *
 * Uses a small `migrations_applied` table (instead of PRAGMA user_version) because
 * migrations now span multiple files across future modules — same idea as the
 * Android app's Migrations.kt, but scaled to a multi-file, multi-module layout.
 */
export async function runMigrations() {
  await db.execute(
    `CREATE TABLE IF NOT EXISTS migrations_applied (
      version INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      applied_at INTEGER NOT NULL
    )`,
  );

  const appliedResult = await db.execute('SELECT version FROM migrations_applied');
  const applied = new Set(appliedResult.rows.map((row) => Number(row.version)));

  const files = (await readdir(migrationsDir))
    .filter((file) => file.endsWith('.js'))
    .sort();

  for (const file of files) {
    const moduleUrl = pathToFileURL(path.join(migrationsDir, file)).href;
    const migration = (await import(moduleUrl)).default;

    if (!migration || typeof migration.up !== 'function' || typeof migration.version !== 'number') {
      throw new Error(`Invalid migration file "${file}" — expected default export { version, name, up }.`);
    }

    if (applied.has(migration.version)) continue;

    await migration.up(db);
    await db.execute({
      sql: 'INSERT INTO migrations_applied (version, name, applied_at) VALUES (?, ?, ?)',
      args: [migration.version, migration.name ?? file, Date.now()],
    });
    console.log(`[migrate] applied ${migration.version}_${migration.name ?? file}`);
  }
}

const isDirectRun = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isDirectRun) {
  runMigrations()
    .then(() => {
      console.log('[migrate] all migrations up to date');
      process.exit(0);
    })
    .catch((error) => {
      console.error('[migrate] failed:', error);
      process.exit(1);
    });
}
