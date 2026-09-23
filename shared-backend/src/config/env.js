import { createHash } from 'node:crypto';
import dotenv from 'dotenv';

dotenv.config();

const REQUIRED_VARS = [
  'TURSO_DATABASE_URL',
  'TURSO_AUTH_TOKEN',
  'PORT',
  'CORS_ALLOWED_ORIGINS',
  'ADMIN_SECRET',
];

function readEnv(name) {
  const value = process.env[name];
  if (value === undefined || value === null || String(value).trim() === '') {
    return null;
  }
  return String(value).trim();
}

function loadEnv() {
  const missing = REQUIRED_VARS.filter((name) => readEnv(name) === null);
  if (missing.length > 0) {
    throw new Error(
      `Missing required environment variable(s): ${missing.join(', ')}. ` +
        'Copy .env.example to .env and fill in every value before starting the server.',
    );
  }

  const port = Number(readEnv('PORT'));
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

  // Session-token signing secret. Falls back to a value derived from
  // ADMIN_SECRET so existing deployments keep working without new config.
  // Set SESSION_SECRET explicitly in production for best practice.
  const sessionSecret =
    readEnv('SESSION_SECRET') ??
    createHash('sha256').update(`pashurakshak-session:${adminSecret}`).digest('hex');

  const mailMode = (readEnv('MAIL_MODE') ?? 'log').toLowerCase();
  if (mailMode !== 'log' && mailMode !== 'smtp') {
    throw new Error(`Invalid MAIL_MODE "${mailMode}" — must be "log" or "smtp".`);
  }

  const smtpPort = Number(readEnv('SMTP_PORT') ?? '587');
  if (!Number.isInteger(smtpPort) || smtpPort < 1 || smtpPort > 65535) {
    throw new Error(`Invalid SMTP_PORT "${process.env.SMTP_PORT}" — must be an integer.`);
  }

  return {
    tursoDatabaseUrl: readEnv('TURSO_DATABASE_URL'),
    tursoAuthToken: readEnv('TURSO_AUTH_TOKEN'),
    port,
    corsAllowedOrigins,
    adminSecret,
    groqApiKey: readEnv('GROQ_API_KEY'),
    sessionSecret,
    mailMode,
    smtpHost: readEnv('SMTP_HOST'),
    smtpPort,
    smtpUser: readEnv('SMTP_USER'),
    smtpPass: readEnv('SMTP_PASS'),
    mailFrom: readEnv('MAIL_FROM') ?? readEnv('SMTP_USER'),
  };
}

export const env = loadEnv();
