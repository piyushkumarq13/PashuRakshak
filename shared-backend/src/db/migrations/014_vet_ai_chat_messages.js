/**
 * Vet AI chats, both standalone and case-scoped.
 *
 * Each row is one message. `report_id` is null for general veterinary chats and
 * set for conversations opened from a specific case. Ownership is always
 * checked with `vet_id`, so one vet can never read another vet's history.
 */
export default {
  version: 15,
  name: '014_vet_ai_chat_messages',
  async up(db) {
    await db.batch(
      [
        `CREATE TABLE IF NOT EXISTS pashu_ai_vet_chat_messages (
          id TEXT PRIMARY KEY,
          conversation_id TEXT NOT NULL,
          vet_id TEXT NOT NULL,
          report_id TEXT,
          role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
          content TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )`,
        `CREATE INDEX IF NOT EXISTS idx_ai_vet_chat_messages_vet_conversation
          ON pashu_ai_vet_chat_messages(vet_id, conversation_id, created_at)`,
        `CREATE INDEX IF NOT EXISTS idx_ai_vet_chat_messages_vet_report
          ON pashu_ai_vet_chat_messages(vet_id, report_id, created_at)`,
        `CREATE INDEX IF NOT EXISTS idx_ai_vet_chat_messages_conversation
          ON pashu_ai_vet_chat_messages(conversation_id, created_at)`,
      ],
      'write',
    );
  },
};
