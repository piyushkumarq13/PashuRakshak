import { createHash } from 'node:crypto';
import fs from 'node:fs';
import dotenv from 'dotenv';

const loadedEnvFiles = [];

function loadEnvFile(file) {
  if (!fs.existsSync(file)) return;
  const result = dotenv.config({ path: file });
  if (!result.error) loadedEnvFiles.push(file);
}

// Render's "Add from .env" sets real environment variables. If the file was
// uploaded as a Secret File instead, it lives in the service root and/or
// /etc/secrets, so load it explicitly. Existing env vars always win.
loadEnvFile('.env');
if (process.env.RENDER === 'true') {
  loadEnvFile('.env.render');
  loadEnvFile('/etc/secrets/.env.render');
  loadEnvFile('/etc/secrets/.env');
}

const REQUIRED_VARS = [
  'TURSO_DATABASE_URL',
  'TURSO_AUTH_TOKEN',
  'CORS_ALLOWED_ORIGINS',
  'ADMIN_SECRET',
];

const MAIL_MODES = ['log', 'smtp', 'brevo', 'resend', 'auto'];

function stripOuterQuotes(value) {
  const trimmed = value.trim();
  if (
    trimmed.length >= 2 &&
    ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
      (trimmed.startsWith("'") && trimmed.endsWith("'")))
  ) {
    return trimmed.slice(1, -1).trim();
  }
  return trimmed;
}

function readEnv(name) {
  const value = process.env[name];
  if (value === undefined || value === null || String(value).trim() === '') {
    return null;
  }
  return stripOuterQuotes(String(value));
}

function loadEnv() {
  const missing = REQUIRED_VARS.filter((name) => readEnv(name) === null);
  if (missing.length > 0) {
    throw new Error(
      `Missing required environment variable(s): ${missing.join(', ')}. ` +
        'Copy .env.example to .env and fill in every value before starting the server.',
    );
  }

  // Render sets PORT automatically; Vercel serverless does not need one.
  const port = Number(readEnv('PORT') ?? '3000');
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`Invalid PORT "${process.env.PORT}" — must be an integer between 1 and 65535.`);
  }

  const corsAllowedOrigins = readEnv('CORS_ALLOWED_ORIGINS')
    .split(',')
    .map((origin) => origin.trim())
    .filter((origin) => origin.length > 0);

  if (corsAllowedOrigins.length === 0) {
    throw new Error(
      'CORS_ALLOWED_ORIGINS must contain at least one origin (comma-separated list).',
    );
  }

  const adminSecret = readEnv('ADMIN_SECRET');
  const nodeEnv = (readEnv('NODE_ENV') ?? 'development').toLowerCase();
  const isProduction = nodeEnv === 'production';

  if (isProduction && corsAllowedOrigins.includes('*')) {
    // Kept as a warning (not fatal): Render sets NODE_ENV=production, and
    // wildcard CORS is needed while Vercel preview URLs are still changing.
    // Prefer listing your exact domains once they are final.
    console.warn(
      '[env] CORS_ALLOWED_ORIGINS contains "*" in production — every origin can call the API. ' +
        'Replace it with your deployed Vercel URLs for tighter security.',
    );
  }

  const configuredSessionSecret = readEnv('SESSION_SECRET');
  if (isProduction && !configuredSessionSecret) {
    throw new Error(
      'SESSION_SECRET is required in production. Generate a long random value and set it in the environment.',
    );
  }

  // Fallback keeps local development working; production must set SESSION_SECRET.
  const sessionSecret =
    configuredSessionSecret ??
    createHash('sha256').update(`pashurakshak-session:${adminSecret}`).digest('hex');

  const mailMode = (readEnv('MAIL_MODE') ?? 'log').toLowerCase();
  if (!MAIL_MODES.includes(mailMode)) {
    throw new Error(`Invalid MAIL_MODE "${mailMode}" — must be one of: ${MAIL_MODES.join(', ')}.`);
  }

  const smtpUser = readEnv('SMTP_USER');
  const smtpPass = readEnv('SMTP_PASS');
  const isBrevoSmtp = Boolean(smtpUser?.toLowerCase().endsWith('@smtp-brevo.com'));
  const isGmailSmtp = /@(gmail|googlemail)\.com$/i.test(smtpUser ?? '');
  // Default the host/port from the login so a partially-filled SMTP config
  // still works. Brevo uses 2525 because Render Free blocks 465/587; Gmail
  // uses 465 (supported by Vercel, not by Render Free).
  const smtpHost =
    readEnv('SMTP_HOST') ??
    (isBrevoSmtp ? 'smtp-relay.brevo.com' : isGmailSmtp ? 'smtp.gmail.com' : null);
  const smtpPort = Number(
    readEnv('SMTP_PORT') ?? (isBrevoSmtp ? '2525' : isGmailSmtp ? '465' : '587'),
  );
  if (!Number.isInteger(smtpPort) || smtpPort < 1 || smtpPort > 65535) {
    throw new Error(`Invalid SMTP_PORT "${process.env.SMTP_PORT}" — must be an integer.`);
  }

  // Brevo API keys start with xkeysib-. If one was pasted into SMTP_PASS by
  // mistake, use it as the API key instead of silently failing.
  const brevoApiKey =
    readEnv('BREVO_API_KEY') ??
    readEnv('BREVO_KEY') ??
    readEnv('SENDINBLUE_API_KEY') ??
    (smtpPass?.startsWith('xkeysib-') ? smtpPass : null);

  return {
    nodeEnv,
    isProduction,
    loadedEnvFiles,
    tursoDatabaseUrl: readEnv('TURSO_DATABASE_URL'),
    tursoAuthToken: readEnv('TURSO_AUTH_TOKEN'),
    port,
    corsAllowedOrigins,
    adminSecret,
    groqApiKey: readEnv('GROQ_API_KEY'),
    sessionSecret,
    mailMode,
    smtpHost,
    smtpPort,
    smtpUser,
    smtpPass,
    // HTTPS email providers (needed on Render Free, which blocks SMTP ports).
    brevoApiKey,
    resendApiKey: readEnv('RESEND_API_KEY'),
    mailFrom: readEnv('MAIL_FROM') ?? smtpUser,
  };
}

export const env = loadEnv();
