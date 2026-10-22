/**
 * Performance indexes for the hot read paths used by the mobile app and the
 * government dashboard. These are additive and do not change any API shape.
 */
export default {
  version: 12,
  name: '011_performance_indexes',
  async up(db) {
    await db.batch(
      [
        'CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)',
        'CREATE INDEX IF NOT EXISTS idx_reports_created_status ON pashu_symptom_reports(created_at, status)',
        'CREATE INDEX IF NOT EXISTS idx_reports_farmer_created ON pashu_symptom_reports(farmer_id, created_at)',
        'CREATE INDEX IF NOT EXISTS idx_reports_photo_url ON pashu_symptom_reports(photo_remote_url)',
        'CREATE INDEX IF NOT EXISTS idx_reports_animal ON pashu_symptom_reports(animal_id)',
        'CREATE INDEX IF NOT EXISTS idx_alerts_recipient_created ON pashu_alerts(recipient_role, recipient_id, created_at)',
        'CREATE INDEX IF NOT EXISTS idx_alerts_created ON pashu_alerts(created_at)',
        'CREATE INDEX IF NOT EXISTS idx_device_tokens_uid ON device_tokens(uid)',
        'CREATE INDEX IF NOT EXISTS idx_device_tokens_app_role ON device_tokens(app_id, role)',
        'CREATE INDEX IF NOT EXISTS idx_vet_applications_status_created ON vet_applications(status, created_at)',
        'CREATE INDEX IF NOT EXISTS idx_gov_alerts_created ON pashu_gov_alerts(created_at)',
        'CREATE INDEX IF NOT EXISTS idx_visit_verifications_report ON pashu_visit_verifications(report_id)',
      ],
      'write',
    );
  },
};
