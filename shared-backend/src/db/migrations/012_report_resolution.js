/**
 * Explicit resolution metadata for report cases.
 *
 * `resolved_at` / `resolved_by` are written when a vet closes a case (including
 * a successful QR visit verification). `resolution_note` keeps the vet's closing
 * note separate from the clinical assessment.
 */
export default {
  version: 13,
  name: '012_report_resolution',
  async up(db) {
    await db.batch(
      [
        'ALTER TABLE pashu_symptom_reports ADD COLUMN resolved_at INTEGER',
        'ALTER TABLE pashu_symptom_reports ADD COLUMN resolved_by TEXT',
        'ALTER TABLE pashu_symptom_reports ADD COLUMN resolution_note TEXT',
        'CREATE INDEX IF NOT EXISTS idx_reports_status_resolved ON pashu_symptom_reports(status, resolved_at)',
      ],
      'write',
    );
  },
};
