/**
 * pashu-health module schema.
 * Table names are prefixed with `pashu_` to avoid collisions with future modules.
 * Columns mirror the Android app's Migrations.kt (animals, vaccinations,
 * symptom_reports, vets, alerts, farmers) exactly.
 */
export default {
  version: 2,
  name: '001_pashu_health',
  async up(db) {
    await db.batch(
      [
        `CREATE TABLE pashu_animals (
          id TEXT PRIMARY KEY,
          owner_farmer_id TEXT NOT NULL,
          species TEXT NOT NULL,
          name TEXT NOT NULL,
          qr_code_id TEXT NOT NULL UNIQUE,
          created_at INTEGER NOT NULL
        )`,
        `CREATE TABLE pashu_vaccinations (
          id TEXT PRIMARY KEY,
          animal_id TEXT NOT NULL REFERENCES pashu_animals(id),
          vaccine_name TEXT NOT NULL,
          date_given INTEGER NOT NULL,
          next_due INTEGER NOT NULL
        )`,
        `CREATE TABLE pashu_symptom_reports (
          id TEXT PRIMARY KEY,
          animal_id TEXT NOT NULL REFERENCES pashu_animals(id),
          farmer_id TEXT NOT NULL,
          symptoms TEXT NOT NULL,
          photo_local_path TEXT NOT NULL,
          photo_remote_url TEXT,
          latitude REAL NOT NULL,
          longitude REAL NOT NULL,
          risk_score INTEGER NOT NULL,
          risk_breakdown TEXT NOT NULL,
          status TEXT NOT NULL CHECK (status IN ('reported', 'vet_assigned', 'examined', 'sample_sent', 'confirmed', 'resolved')),
          synced INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL
        )`,
        `CREATE TABLE pashu_vets (
          id TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          phone TEXT NOT NULL,
          assigned_village TEXT NOT NULL
        )`,
        `CREATE TABLE pashu_alerts (
          id TEXT PRIMARY KEY,
          recipient_role TEXT NOT NULL,
          recipient_id TEXT NOT NULL,
          message TEXT NOT NULL,
          read INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL
        )`,
        `CREATE TABLE pashu_farmers (
          id TEXT PRIMARY KEY,
          phone TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )`,
      ],
      'write',
    );
  },
};
