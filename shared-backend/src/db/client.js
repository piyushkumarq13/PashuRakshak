import { createClient } from '@libsql/client';
import { env } from '../config/env.js';

/**
 * Remote libSQL/Turso client.
 *
 * A hung database connection must never leave an HTTP request (or the whole
 * server) waiting indefinitely. The custom fetch applies a hard timeout to
 * every database round-trip; the concurrency cap prevents request bursts from
 * opening an unbounded number of sockets at once.
 */
const DB_TIMEOUT_MS = 12_000;
const DB_CONCURRENCY = 10;

function fetchWithTimeout(input, init = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), DB_TIMEOUT_MS);
  timer.unref?.();

  const externalSignal = init.signal;
  if (externalSignal) {
    if (externalSignal.aborted) controller.abort();
    else externalSignal.addEventListener('abort', () => controller.abort(), { once: true });
  }

  return fetch(input, { ...init, signal: controller.signal }).finally(() => clearTimeout(timer));
}

export const db = createClient({
  url: env.tursoDatabaseUrl,
  authToken: env.tursoAuthToken,
  fetch: fetchWithTimeout,
  concurrency: DB_CONCURRENCY,
});
