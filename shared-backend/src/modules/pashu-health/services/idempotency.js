/**
 * Idempotency-Key support for offline-sync pushes. Clients attach
 * `Idempotency-Key: <stable-id>` to retryable POSTs; we cache the first
 * successful JSON response per (key, endpoint) and replay it on duplicates so
 * retries never create duplicate effects.
 */
function scopedKey(req) {
  const raw = req.get('Idempotency-Key');
  if (!raw) return null;
  // Scope by caller so two accounts reusing the same report id/key cannot
  // replay each other's cached response.
  const actor = req.userId ?? req.magistrateId ?? 'anonymous';
  return `${actor}:${raw}`;
}

export async function idempotencyReplay(db, req, res) {
  const key = scopedKey(req);
  if (!key) return false;
  try {
    const existing = await db.execute({
      sql: 'SELECT response_json FROM idempotency_keys WHERE key = ? AND endpoint = ?',
      args: [key, req.originalUrl],
    });
    if (existing.rows.length > 0) {
      try {
        const body = JSON.parse(String(existing.rows[0].response_json));
        res.status(200).json(body);
        return true;
      } catch {
        return false;
      }
    }
  } catch (error) {
    // Losing the cache must not turn a retry into a 500; let the request run.
    console.error('[idempotency] lookup failed:', error?.message ?? error);
  }
  return false;
}

export async function idempotencyStore(db, req, body) {
  const key = scopedKey(req);
  if (!key) return;
  try {
    await db.execute({
      sql: `INSERT OR REPLACE INTO idempotency_keys (key, endpoint, response_json, created_at)
            VALUES (?, ?, ?, ?)`,
      args: [key, req.originalUrl, JSON.stringify(body), Date.now()],
    });
  } catch (error) {
    console.error('[idempotency] store failed:', error?.message ?? error);
  }
}
