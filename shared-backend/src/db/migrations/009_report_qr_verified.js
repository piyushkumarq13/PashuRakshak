/**
 * Server-side QR-verification truth on reports: qr_verified / verified_at /
 * verified_by are written ONLY by routes/visits.js on a successful QR match.
 * Clients must not be able to set them via the reports upsert.
 *
 * File name follows the existing <version>_<name>.js convention; version is 10
 * because 008_report_qr_disease.js already occupies version 9.
 */
export default {
  version: 10,
  name: '009_report_qr_verified',
  async up(db) {
    await db.batch(
      [
        `ALTER TABLE pashu_symptom_reports ADD COLUMN qr_verified INTEGER NOT NULL DEFAULT 0`,
        `ALTER TABLE pashu_symptom_reports ADD COLUMN verified_at INTEGER`,
        `ALTER TABLE pashu_symptom_reports ADD COLUMN verified_by TEXT`,
      ],
      'write',
    );
  },
};
