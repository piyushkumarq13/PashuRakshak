import nodemailer from 'nodemailer';
import { env } from '../../config/env.js';

/**
 * Email delivery for OTPs.
 *
 * MAIL_MODE:
 *   log    → print the email to the server console (development).
 *   smtp   → send through SMTP (Gmail app password). Works locally and on
 *            Vercel, but Render Free blocks outbound SMTP ports 25/465/587.
 *   brevo  → send through Brevo's HTTPS API (works on Render Free).
 *   resend → send through Resend's HTTPS API (works on Render Free).
 *   auto   → Brevo → Resend → SMTP → log, whichever is configured first.
 *
 * Delivery never throws: the caller always gets { sent, mode, error? }.
 */

const HTTP_TIMEOUT_MS = 12_000;
const BREVO_API = 'https://api.brevo.com/v3';
const warnings = new Set();
let transporter = null;

function smtpConfigured() {
  return Boolean(env.smtpHost && env.smtpUser && env.smtpPass);
}

function warnFallback(mode) {
  const key = `${env.mailMode}:${mode}`;
  if (warnings.has(key)) return;
  warnings.add(key);
  console.warn(
    `[mailer] MAIL_MODE=${env.mailMode} is not fully configured — using ${mode} instead.`,
  );
}

export function resolveMailMode() {
  switch (env.mailMode) {
    case 'brevo':
      if (env.brevoApiKey) return 'brevo';
      warnFallback(smtpConfigured() ? 'smtp' : 'log');
      return smtpConfigured() ? 'smtp' : 'log';
    case 'resend':
      if (env.resendApiKey) return 'resend';
      warnFallback(smtpConfigured() ? 'smtp' : 'log');
      return smtpConfigured() ? 'smtp' : 'log';
    case 'smtp':
      if (smtpConfigured()) return 'smtp';
      warnFallback('log');
      return 'log';
    case 'auto':
      if (env.brevoApiKey) return 'brevo';
      if (env.resendApiKey) return 'resend';
      if (smtpConfigured()) return 'smtp';
      warnFallback('log');
      return 'log';
    default:
      return 'log';
  }
}

function getTransporter() {
  if (!smtpConfigured()) return null;
  if (!transporter) {
    transporter = nodemailer.createTransport({
      host: env.smtpHost,
      port: env.smtpPort,
      secure: env.smtpPort === 465,
      requireTLS: env.smtpPort === 587,
      auth: { user: env.smtpUser, pass: env.smtpPass },
      connectionTimeout: 10_000,
      greetingTimeout: 10_000,
      socketTimeout: 20_000,
      tls: { minVersion: 'TLSv1.2' },
    });
  }
  return transporter;
}

function cleanEmail(value) {
  return String(value ?? '')
    .trim()
    .replace(/^["']|["']$/g, '')
    .trim();
}

function parseFrom(value, fallbackEmail) {
  const raw = String(value ?? '').trim();
  const match = /^(.*)<([^>]+)>\s*$/.exec(raw);
  if (match) {
    const name = match[1].trim().replace(/^"|"$/g, '').trim() || 'PashuRakshak';
    return { name, email: cleanEmail(match[2]) };
  }
  return { name: 'PashuRakshak', email: cleanEmail(raw || fallbackEmail) };
}

export function getMailerStatus() {
  const from = parseFrom(env.mailFrom, env.smtpUser);
  return {
    configuredMode: env.mailMode,
    resolvedMode: resolveMailMode(),
    from: from.email ? maskEmail(from.email) : null,
    smtpConfigured: smtpConfigured(),
    brevoConfigured: Boolean(env.brevoApiKey),
    brevoKeyLooksValid: Boolean(env.brevoApiKey?.startsWith('xkeysib-')),
    resendConfigured: Boolean(env.resendApiKey),
  };
}

/** Logs a safe, secret-free summary once per boot so Render logs show the mail setup. */
export function logMailerStartup() {
  const status = getMailerStatus();
  console.log(
    `[mailer] configured=${status.configuredMode} resolved=${status.resolvedMode} ` +
      `from=${status.from ?? '(missing)'} ` +
      `brevoKey=${status.brevoConfigured ? (status.brevoKeyLooksValid ? 'set' : 'set-but-suspicious') : 'missing'} ` +
      `smtp=${status.smtpConfigured ? 'set' : 'missing'} resend=${status.resendConfigured ? 'set' : 'missing'}`,
  );

  if (status.configuredMode === 'brevo' && !status.brevoConfigured) {
    console.error('[mailer] MAIL_MODE=brevo but BREVO_API_KEY is missing — emails will not send.');
  }
  if (status.brevoConfigured && !status.brevoKeyLooksValid) {
    console.error(
      '[mailer] BREVO_API_KEY does not start with "xkeysib-". ' +
        'A Brevo SMTP key (xsmtpsib-...) will NOT work with the HTTPS API.',
    );
  }
  if (status.resolvedMode === 'brevo' && !status.from) {
    console.error('[mailer] BREVO is active but MAIL_FROM/SMTP_USER is missing — emails will not send.');
  }
}

/** Checks the Brevo API key and account status. Returns secrets-free details. */
export async function checkBrevoAccount() {
  if (!env.brevoApiKey) {
    return { ok: false, error: 'BREVO_API_KEY is not set.' };
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HTTP_TIMEOUT_MS);
  try {
    const response = await fetch(`${BREVO_API}/account`, {
      headers: { 'api-key': env.brevoApiKey, accept: 'application/json' },
      signal: controller.signal,
    });
    const text = await response.text().catch(() => '');
    if (!response.ok) {
      return { ok: false, status: response.status, error: text.slice(0, 400) || response.statusText };
    }
    let account = {};
    try {
      account = text ? JSON.parse(text) : {};
    } catch {
      account = {};
    }
    return {
      ok: true,
      status: response.status,
      email: account.email ? maskEmail(account.email) : null,
      companyName: account.companyName ?? null,
    };
  } catch (error) {
    return {
      ok: false,
      error:
        error?.name === 'AbortError'
          ? 'Brevo account check timed out.'
          : (error?.message ?? String(error)),
    };
  } finally {
    clearTimeout(timer);
  }
}

async function postJson(url, headers, body) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HTTP_TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', ...headers },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    const text = await response.text().catch(() => '');
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${text.slice(0, 400) || response.statusText}`);
    }
    return text;
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error(`Request timed out after ${HTTP_TIMEOUT_MS / 1000}s`);
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

async function sendViaBrevo({ to, subject, text, html }) {
  const from = parseFrom(env.mailFrom, env.smtpUser);
  if (!from.email) throw new Error('MAIL_FROM (or SMTP_USER) is required for Brevo.');

  const payload = {
    sender: { name: from.name, email: from.email },
    to: [{ email: to }],
    subject,
    textContent: text,
    ...(html ? { htmlContent: html } : {}),
  };

  // Retry once on rate-limit / temporary Brevo failures.
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    try {
      await postJson(
        `${BREVO_API}/smtp/email`,
        { 'api-key': env.brevoApiKey, accept: 'application/json' },
        payload,
      );
      return;
    } catch (error) {
      const retryable = /^HTTP (429|5\d\d):/.test(error?.message ?? '');
      if (attempt === 2 || !retryable) throw error;
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }
}

async function sendViaResend({ to, subject, text, html }) {
  const from = env.mailFrom || env.smtpUser;
  if (!from) throw new Error('MAIL_FROM (or SMTP_USER) is required for Resend.');
  await postJson(
    'https://api.resend.com/emails',
    { authorization: `Bearer ${env.resendApiKey}` },
    {
      from,
      to: [to],
      subject,
      text,
      ...(html ? { html } : {}),
    },
  );
}

/** Sends { to, subject, text, html }. Never throws — returns { sent, mode }. */
export async function sendMail({ to, subject, text, html }) {
  const mode = resolveMailMode();

  if (mode === 'log') {
    console.warn(`[mailer] EMAIL (log mode) → ${to}\nSubject: ${subject}\n${text}`);
    return { sent: false, mode: 'log' };
  }

  try {
    if (mode === 'smtp') {
      const transport = getTransporter();
      if (!transport) throw new Error('SMTP is not configured.');
      await transport.sendMail({
        from: env.mailFrom || env.smtpUser,
        to,
        subject,
        text,
        html: html ?? undefined,
      });
    } else if (mode === 'brevo') {
      await sendViaBrevo({ to, subject, text, html });
    } else if (mode === 'resend') {
      await sendViaResend({ to, subject, text, html });
    } else {
      throw new Error(`Unsupported mail mode "${mode}".`);
    }
    return { sent: true, mode };
  } catch (error) {
    // Never fail the whole request because email delivery broke — log loudly
    // (the OTP is visible in the log so the flow can still be completed in dev).
    console.error(`[mailer] send to ${maskEmail(to)} via ${mode} failed:`, error?.message ?? error);
    if (mode === 'smtp' && env.isProduction) {
      console.error(
        '[mailer] Render Free blocks outbound SMTP (ports 25/465/587). ' +
          'Use MAIL_MODE=brevo (or resend), or upgrade the Render instance to a paid plan.',
      );
    }
    console.warn(
      `[mailer] EMAIL (fallback log after ${mode} error) → ${maskEmail(to)}\nSubject: ${subject}\n${text}`,
    );
    return { sent: false, mode, error: error?.message };
  }
}

export function maskEmail(email) {
  const [local = '', domain = ''] = String(email).split('@');
  const visible = local.slice(0, Math.min(2, local.length));
  return `${visible}${'*'.repeat(Math.max(local.length - visible.length, 1))}@${domain}`;
}
