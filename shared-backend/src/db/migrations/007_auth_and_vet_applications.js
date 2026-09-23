/**
 * Custom auth (no Firebase Auth) + vet application approval workflow.
 *
 * - users: adds pin_hash / pin_type / email_verified; normalizes phone to
 *   canonical 10-digit form (strips +91 / spaces / dashes / leading 0/91)
 *   so lookups are exact-match from now on.
 * - auth_otps: email OTP codes (hashed at rest).
 * - vet_applications: registration applications reviewed by the district
 *   magistrate derived from the application pincode.
 * - gov_magistrates: seeded district magistrate accounts (email + OTP login).
 */
function canonicalPhoneSql(column) {
  const stripped = `REPLACE(REPLACE(REPLACE(${column}, '+', ''), ' ', ''), '-', '')`;
  return `CASE
    WHEN LENGTH(${stripped}) = 12 AND SUBSTR(${stripped}, 1, 2) = '91'
      THEN SUBSTR(${stripped}, 3)
    WHEN LENGTH(${stripped}) = 11 AND SUBSTR(${stripped}, 1, 1) = '0'
      THEN SUBSTR(${stripped}, 2)
    ELSE ${stripped}
  END`;
}

export default {
  version: 8,
  name: '007_auth_and_vet_applications',
  async up(db) {
    await db.batch(
      [
        `ALTER TABLE users ADD COLUMN pin_hash TEXT`,
        `ALTER TABLE users ADD COLUMN pin_type TEXT CHECK (pin_type IN ('pin', 'password'))`,
        `ALTER TABLE users ADD COLUMN email_verified INTEGER NOT NULL DEFAULT 0`,
        `UPDATE users SET phone = ${canonicalPhoneSql('phone')} WHERE phone IS NOT NULL`,
        `CREATE TABLE auth_otps (
          id TEXT PRIMARY KEY,
          email TEXT NOT NULL,
          purpose TEXT NOT NULL,
          phone TEXT,
          code_hash TEXT NOT NULL,
          attempts INTEGER NOT NULL DEFAULT 0,
          expires_at INTEGER NOT NULL,
          consumed INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL
        )`,
        `CREATE INDEX idx_auth_otps_email_purpose ON auth_otps (email, purpose, created_at)`,
        `CREATE TABLE vet_applications (
          id TEXT PRIMARY KEY,
          user_id TEXT NOT NULL UNIQUE REFERENCES users(id),
          status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
          full_name TEXT NOT NULL,
          phone TEXT,
          email TEXT,
          qualification TEXT NOT NULL,
          license_number TEXT NOT NULL,
          experience_years INTEGER,
          clinic_name TEXT,
          address TEXT NOT NULL,
          village TEXT NOT NULL,
          pincode TEXT NOT NULL,
          district TEXT NOT NULL,
          state TEXT,
          service_areas TEXT NOT NULL,
          review_note TEXT,
          reviewed_by TEXT,
          reviewed_at INTEGER,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        )`,
        `CREATE INDEX idx_vet_applications_district_status ON vet_applications (district, status)`,
        `CREATE TABLE gov_magistrates (
          id TEXT PRIMARY KEY,
          email TEXT NOT NULL UNIQUE,
          name TEXT NOT NULL,
          district TEXT NOT NULL,
          state TEXT,
          created_at INTEGER NOT NULL
        )`,
      ],
      'write',
    );
  },
};
