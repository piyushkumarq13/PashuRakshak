/**
 * Clean up literal "null" strings written by older Android clients.
 *
 * Android's JSONObject.optString() serializes a JSON null as the string
 * "null". That made assigned_vet_id, qr_code_id, photo_remote_url and other
 * optional report fields look populated, which blocked vet visit assignment
 * checks and QR verification. New clients and routes no longer write these
 * values; this migration repairs rows that already contain them.
 */
export default {
  version: 16,
  name: '015_sanitize_literal_nulls',
  async up(db) {
    const columns = [
      'assigned_vet_id',
      'vet_assessment',
      'village',
      'qr_code_id',
      'photo_remote_url',
      'disease_description',
      'resolved_by',
      'verified_by',
      'resolution_note',
      'ai_risk_category',
    ];
    await db.batch(
      columns.map(
        (column) =>
          `UPDATE pashu_symptom_reports SET ${column} = NULL WHERE lower(${column}) IN ('null', 'undefined')`,
      ),
      'write',
    );
  },
};
