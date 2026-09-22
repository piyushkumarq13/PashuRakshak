import cors from 'cors';
import express from 'express';
import { env } from './config/env.js';
import { runMigrations } from './db/migrate.js';
import routes from './routes.js';

const app = express();

app.use(
  cors({
    origin(origin, callback) {
      // No Origin header (curl, server-to-server) or an allow-listed origin → allow.
      if (!origin || env.corsAllowedOrigins.includes(origin)) {
        callback(null, true);
      } else {
        callback(null, false);
      }
    },
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  }),
);

app.use(express.json());

app.get('/health', (_req, res) => {
  res.status(200).json({ status: 'ok' });
});

app.use('/api/v1', routes);

app.use((_req, res) => {
  res.status(404).json({ error: 'not_found', message: 'Route not found.' });
});

app.use((error, _req, res, _next) => {
  if (error?.type === 'entity.parse.failed') {
    return res.status(400).json({ error: 'invalid_json', message: 'Request body is not valid JSON.' });
  }
  console.error('[app] unhandled error:', error);
  return res.status(500).json({ error: 'internal_error', message: 'Unexpected server error.' });
});

async function start() {
  await runMigrations();
  app.listen(env.port, () => {
    console.log(`[app] shared-backend listening on http://localhost:${env.port}`);
    console.log(`[app] health check: http://localhost:${env.port}/health`);
  });
}

start().catch((error) => {
  console.error('[app] failed to start:', error);
  process.exit(1);
});
