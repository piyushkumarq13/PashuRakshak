import { timingSafeEqual } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import cors from 'cors';
import express from 'express';
import { env } from './config/env.js';
import { runMigrations } from './db/migrate.js';
import { getMailerStatus, logMailerStartup } from './core/services/mailer.js';
import { clearOtps, listOtps } from './core/services/otpLog.js';
import routes from './routes.js';

const app = express();

// Log a secret-free mail summary on every boot/cold start so Render logs make
// misconfiguration obvious (missing key, SMTP key used as API key, etc.).
logMailerStartup();

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
app.use(express.urlencoded({ extended: false }));

app.get('/health', (_req, res) => {
  const mail = getMailerStatus();
  res.status(200).json({
    status: 'ok',
    mail: {
      configuredMode: mail.configuredMode,
      resolvedMode: mail.resolvedMode,
      from: mail.from,
      brevoConfigured: mail.brevoConfigured,
      brevoKeyLooksValid: mail.brevoKeyLooksValid,
      smtpConfigured: mail.smtpConfigured,
    },
  });
});

// ── OTP debug page ────────────────────────────────────────────────────────
// Simple PIN-protected page for testing the registration flow without email.
// Open /otp and enter the PIN. Override the PIN with OTP_PAGE_PIN.
const OTP_PAGE_PIN = process.env.OTP_PAGE_PIN ?? '841428';
const OTP_PAGE_COOKIE = 'otp_page_pin';
const OTP_PAGE_COOKIE_MAX_AGE = 60 * 60 * 1000;

function readCookie(req, name) {
  const header = req.headers.cookie ?? '';
  for (const part of header.split(';')) {
    const index = part.indexOf('=');
    if (index === -1) continue;
    if (part.slice(0, index).trim() !== name) continue;
    const value = part.slice(index + 1).trim();
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  }
  return null;
}

function otpPin(req) {
  return String(
    req.body?.pin ??
      req.query.pin ??
      req.get('x-otp-pin') ??
      readCookie(req, OTP_PAGE_COOKIE) ??
      '',
  );
}

function otpAuthOk(req) {
  return safeEqual(otpPin(req), OTP_PAGE_PIN);
}

function renderOtpPage(req, res, { error = '', status = 200 } = {}) {
  const authenticated = otpAuthOk(req);
  const items = authenticated ? listOtps() : [];
  const rows = items
    .map((e) => {
      const when = new Date(e.at).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' });
      const delivery = e.delivery.sent
        ? `emailed (${escapeHtml(e.delivery.mode)})`
        : `not sent (${escapeHtml(e.delivery.mode)})${e.delivery.error ? ` — ${escapeHtml(e.delivery.error)}` : ''}`;
      return `<tr>
        <td class="code">${escapeHtml(e.code)}</td>
        <td>${escapeHtml(e.to)}</td>
        <td>${escapeHtml(e.purpose)}</td>
        <td>${delivery}</td>
        <td>${when}</td>
      </tr>`;
    })
    .join('');

  const body = authenticated
    ? `<p class="muted">Auto-refreshes every 3 seconds. <a href="/otp/lock">Lock</a></p>
       ${
         items.length === 0
           ? '<p>No OTPs yet. Trigger a registration or login.</p>'
           : `<table>
         <thead><tr><th>Code</th><th>Email</th><th>Purpose</th><th>Delivery</th><th>Time (IST)</th></tr></thead>
         <tbody>${rows}</tbody>
       </table>`
       }
       <form method="post" action="/otp/clear"><button type="submit">Clear log</button></form>`
    : `<form method="post" action="/otp">
         <label>PIN <input type="password" name="pin" inputmode="numeric" maxlength="6" autofocus required /></label>
         <button type="submit">Open</button>
       </form>
       ${error ? `<p class="error">${escapeHtml(error)}</p>` : ''}`;

  res.status(status).type('html').send(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  ${authenticated ? '<meta http-equiv="refresh" content="3" />' : ''}
  <title>PashuRakshak OTP</title>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 900px; margin: 2rem auto; padding: 0 1rem; color: #111; }
    input, button { font: inherit; padding: .5rem .7rem; }
    input { width: 8rem; letter-spacing: .2em; }
    table { border-collapse: collapse; width: 100%; margin: 1rem 0; }
    th, td { border: 1px solid #bbb; padding: .5rem; text-align: left; }
    th { background: #eee; }
    .code { font-family: monospace; font-size: 1.3rem; font-weight: 700; }
    .error { color: #b00020; }
    .muted { color: #666; }
  </style>
</head>
<body>
  <h1>PashuRakshak OTP log</h1>
  ${body}
</body>
</html>`);
}

app.get('/otp', (req, res) => {
  renderOtpPage(req, res);
});

app.post('/otp', (req, res) => {
  if (!otpAuthOk(req)) {
    return renderOtpPage(req, res, { error: 'Wrong PIN.', status: 401 });
  }
  res.cookie(OTP_PAGE_COOKIE, OTP_PAGE_PIN, {
    httpOnly: true,
    sameSite: 'strict',
    secure: env.isProduction,
    maxAge: OTP_PAGE_COOKIE_MAX_AGE,
    path: '/',
  });
  res.redirect(303, '/otp');
});

app.get('/otp.json', (req, res) => {
  if (!otpAuthOk(req)) {
    return res.status(401).json({ error: 'unauthorized', message: 'Open /otp and enter the PIN.' });
  }
  res.status(200).json({ count: listOtps().length, otps: listOtps() });
});

app.post('/otp/clear', (req, res) => {
  if (!otpAuthOk(req)) {
    return res.status(401).json({ error: 'unauthorized', message: 'Open /otp and enter the PIN.' });
  }
  clearOtps();
  res.redirect(303, '/otp');
});

app.get('/otp/lock', (req, res) => {
  res.clearCookie(OTP_PAGE_COOKIE, { path: '/' });
  res.redirect(303, '/otp');
});

function safeEqual(left, right) {
  const a = Buffer.from(String(left ?? ''));
  const b = Buffer.from(String(right ?? ''));
  return a.length === b.length && timingSafeEqual(a, b);
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

// Exported for serverless hosts (Vercel's `api/index.js`); they import the
// Express app instead of starting a long-running listener.
export { app };
export default app;

// Only listen when run directly (`npm start`, `node src/app.js`).
const isDirectRun =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isDirectRun) {
  start().catch((error) => {
    console.error('[app] failed to start:', error);
    process.exit(1);
  });
}
