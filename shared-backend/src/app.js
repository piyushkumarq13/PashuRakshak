import cors from 'cors';
import express from 'express';
import { env } from './config/env.js';
import { runMigrations } from './db/migrate.js';
import { clearOtps, listOtps } from './core/services/otpLog.js';
import routes from './routes.js';

const app = express();

app.use(
  cors({
    origin(origin, callback) {
      // No Origin header (curl, server-to-server) → allow.
      // "*" in CORS_ALLOWED_ORIGINS → allow every browser origin.
      // Otherwise the origin must be in the allow-list.
      const allowAll = env.corsAllowedOrigins.includes('*');
      if (!origin || allowAll || env.corsAllowedOrigins.includes(origin)) {
        callback(null, true);
      } else {
        callback(null, false);
      }
    },
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  }),
);

app.use(express.json({ limit: '2mb' }));

app.get('/health', (_req, res) => {
  res.status(200).json({ status: 'ok' });
});

// ── OTP debug page (temporary while SMTP is being fixed) ──────────────────
// Set OTP_PAGE=false in the environment to disable.
const otpPageEnabled = (process.env.OTP_PAGE ?? 'true').toLowerCase() !== 'false';

function otpAuthOk(req) {
  // Open when OTP_PAGE_OPEN=true; otherwise require ?key=<ADMIN_SECRET>
  // (or X-Admin-Secret header) so strangers can't harvest codes.
  if ((process.env.OTP_PAGE_OPEN ?? '').toLowerCase() === 'true') return true;
  const key = req.query.key ?? req.get('x-admin-secret');
  return typeof key === 'string' && key.length > 0 && key === env.adminSecret;
}

if (otpPageEnabled) {
  app.get('/otp', (req, res) => {
    if (!otpAuthOk(req)) {
      return res
        .status(401)
        .type('text/plain')
        .send('Provide the admin key: /otp?key=<ADMIN_SECRET>');
    }

    const items = listOtps();
    const rows = items
      .map((e) => {
        const when = new Date(e.at).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' });
        const delivery = e.delivery.sent
          ? `<span class="ok">emailed (${e.delivery.mode})</span>`
          : `<span class="warn">email not sent (${e.delivery.mode})${e.delivery.error ? ` — ${escapeHtml(e.delivery.error)}` : ''}</span>`;
        return `<tr>
          <td class="code">${escapeHtml(e.code)}</td>
          <td>${escapeHtml(e.to)}</td>
          <td>${escapeHtml(e.purpose)}</td>
          <td>${delivery}</td>
          <td>${when}</td>
        </tr>`;
      })
      .join('');

    res.type('html').send(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta http-equiv="refresh" content="3" />
  <title>PashuRakshak OTP log</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 2rem; background: #0f1220; color: #e8eaf6; }
    h1 { font-size: 1.25rem; }
    .banner { background: #3d2b00; border: 1px solid #f0a000; color: #ffd666; padding: .75rem 1rem; border-radius: 8px; margin-bottom: 1rem; }
    table { border-collapse: collapse; width: 100%; font-size: .95rem; }
    th, td { border: 1px solid #2a2f4a; padding: .55rem .75rem; text-align: left; }
    th { background: #1a1f38; }
    tr:nth-child(even) { background: #141830; }
    .code { font-family: ui-monospace, monospace; font-size: 1.35rem; letter-spacing: .2em; font-weight: 700; color: #7cffb2; }
    .ok { color: #7cffb2; }
    .warn { color: #ffb86b; }
    .empty { color: #9aa0b8; padding: 1rem 0; }
    form { margin: .5rem 0 1rem; }
    button { background: #2a2f4a; color: #e8eaf6; border: 1px solid #4a507a; border-radius: 6px; padding: .4rem .9rem; cursor: pointer; }
    a { color: #8ab4ff; }
  </style>
</head>
<body>
  <h1>PashuRakshak — OTP log</h1>
  <div class="banner">
    Debug page showing codes for testing while email SMTP is broken.
    Auto-refreshes every 3s. Disable with <code>OTP_PAGE=false</code>.
    Keep this URL secret.
  </div>
  ${
    items.length === 0
      ? '<p class="empty">No OTPs yet. Trigger a registration or login to see codes here.</p>'
      : `<table>
    <thead><tr><th>Code</th><th>Email</th><th>Purpose</th><th>Delivery</th><th>Time (IST)</th></tr></thead>
    <tbody>${rows}</tbody>
  </table>`
  }
  <form method="post" action="/otp/clear?key=${encodeURIComponent(req.query.key ?? '')}">
    <button type="submit">Clear log</button>
  </form>
  <p><a href="/otp.json?key=${encodeURIComponent(req.query.key ?? '')}">JSON</a></p>
</body>
</html>`);
  });

  app.get('/otp.json', (req, res) => {
    if (!otpAuthOk(req)) {
      return res.status(401).json({ error: 'unauthorized', message: 'Provide ?key=<ADMIN_SECRET>.' });
    }
    res.status(200).json({ count: listOtps().length, otps: listOtps() });
  });

  app.post('/otp/clear', (req, res) => {
    if (!otpAuthOk(req)) {
      return res.status(401).json({ error: 'unauthorized', message: 'Provide ?key=<ADMIN_SECRET>.' });
    }
    clearOtps();
    res.redirect(303, `/otp?key=${encodeURIComponent(String(req.query.key ?? ''))}`);
  });
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

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
