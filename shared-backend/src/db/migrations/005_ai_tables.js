/**
 * AI advisory tables — pashu_ai_responses and pashu_ai_conversations.
 * Stores AI-generated advisory text and full conversation history.
 */
export default {
  version: 6,
  name: '005_ai_tables',
  async up(db) {
    await db.batch(
      [
        `CREATE TABLE pashu_ai_responses (
          id TEXT PRIMARY KEY,
          report_id TEXT NOT NULL REFERENCES pashu_symptom_reports(id),
          farmer_id TEXT NOT NULL REFERENCES users(id),
          response_text TEXT NOT NULL,
          model TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )`,
        `CREATE TABLE pashu_ai_conversations (
          id TEXT PRIMARY KEY,
          report_id TEXT NOT NULL REFERENCES pashu_symptom_reports(id),
          farmer_id TEXT NOT NULL REFERENCES users(id),
          full_history TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        )`,
      ],
      'write',
    );
  },
};
