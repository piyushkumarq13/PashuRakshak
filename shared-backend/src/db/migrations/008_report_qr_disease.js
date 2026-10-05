/**
 * Report-level QR code + free-text disease description.
 * qr_code_id ties a physical/on-screen report QR to exactly one report so a vet
 * can only verify a visit against that report (see routes/visits.js).
 */
export default {
  version: 9,
  name: '008_report_qr_disease',
  async up(db) {
    await db.batch(
      [
        `ALTER TABLE pashu_symptom_reports ADD COLUMN qr_code_id TEXT`,
        `ALTER TABLE pashu_symptom_reports ADD COLUMN disease_description TEXT`,
      ],
      'write',
    );
  },
};
