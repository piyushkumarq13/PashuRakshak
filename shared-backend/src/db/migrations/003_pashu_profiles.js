/**
 * pashu-health module profiles — pashu_ prefixed tables.
 * Farmer and vet profile tables linked to the core users table.
 */
export default {
  version: 4,
  name: '003_pashu_profiles',
  async up(db) {
    await db.batch(
      [
        `CREATE TABLE pashu_farmer_profiles (
          user_id TEXT PRIMARY KEY REFERENCES users(id),
          animal_count INTEGER,
          village TEXT,
          pincode TEXT,
          created_at INTEGER NOT NULL
        )`,
        `CREATE TABLE pashu_vet_profiles (
          user_id TEXT PRIMARY KEY REFERENCES users(id),
          pincode TEXT,
          service_areas TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        )`,
      ],
      'write',
    );
  },
};
