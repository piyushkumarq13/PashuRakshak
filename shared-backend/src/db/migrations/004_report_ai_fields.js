/**
 * pashu-health AI risk categorization + vet assignment fields.
 * Adds columns to pashu_symptom_reports for ai_risk_category,
 * assigned_vet_id, vet_assessment, and village.
 */
export default {
  version: 5,
  name: '004_report_ai_fields',
  async up(db) {
    await db.batch(
      [
        `ALTER TABLE pashu_symptom_reports ADD COLUMN assigned_vet_id TEXT`,
        `ALTER TABLE pashu_symptom_reports ADD COLUMN ai_risk_category TEXT`,
        `ALTER TABLE pashu_symptom_reports ADD COLUMN vet_assessment TEXT`,
        `ALTER TABLE pashu_symptom_reports ADD COLUMN village TEXT`,
      ],
      'write',
    );
  },
};
