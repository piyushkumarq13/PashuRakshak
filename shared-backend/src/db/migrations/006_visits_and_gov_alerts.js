/**
 * Visit verification + government alert readiness tables.
 * Creates pashu_visit_verifications and pashu_gov_alerts.
 */
export default {
  version: 7,
  name: '006_visits_and_gov_alerts',
  async up(db) {
    await db.batch(
      [
        `CREATE TABLE pashu_visit_verifications (
          id TEXT PRIMARY KEY,
          report_id TEXT NOT NULL REFERENCES pashu_symptom_reports(id),
          vet_id TEXT NOT NULL REFERENCES users(id),
          scanned_qr_code_id TEXT,
          matched INTEGER,
          latitude REAL,
          longitude REAL,
          verified_at INTEGER NOT NULL
        )`,
        `CREATE TABLE pashu_gov_alerts (
          id TEXT PRIMARY KEY,
          report_id TEXT NOT NULL REFERENCES pashu_symptom_reports(id),
          severity TEXT NOT NULL CHECK (severity IN ('high', 'medium', 'low')),
          message TEXT NOT NULL,
          acknowledged INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL
        )`,
      ],
      'write',
    );
  },
};
