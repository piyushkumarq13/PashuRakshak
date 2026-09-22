import { createClient } from '@libsql/client';
import { env } from '../config/env.js';

export const db = createClient({
  url: env.tursoDatabaseUrl,
  authToken: env.tursoAuthToken,
});
