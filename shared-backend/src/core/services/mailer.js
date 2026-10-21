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

function parseFrom(value, fallbackEmail) {
  const raw = String(value ?? '').trim();
  const match = /^(.*)<([^>]+)>\s*$/.exec(raw);
  if (match) {
    const name = match[1].trim().replace(/^"|"$/g, '') || 'PashuRakshak';
    return { name, email: match[2].trim() };
  }
  return { name: 'PashuRakshak', email: raw || String(fallbackEmail ?? '').trim() };
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
  } finally {
    clearTimeout(timer);
  }
}

async function sendViaBrevo({ to, subject, text, html }) {
  const from = parseFrom(env.mailFrom, env.smtpUser);
  if (!from.email) throw new Error('MAIL_FROM (or SMTP_USER) is required for Brevo.');
  await postJson(
    'https://api.brevo.com/v3/smtp/email',
    { 'api-key': env.brevoApiKey, accept: 'application/json' },
    {
      sender: { name: from.name, email: from.email },
      to: [{ email: to }],
      subject,
      textContent: text,
      ...(html ? { htmlContent: html } : {}),
    },
  );
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
    console.error(`[mailer] send to ${to} via ${mode} failed:`, error?.message ?? error);
    if (mode === 'smtp' && env.isProduction) {
      console.error(
        '[mailer] Render Free blocks outbound SMTP (ports 25/465/587). ' +
          'Use MAIL_MODE=brevo (or resend), or upgrade the Render instance to a paid plan.',
      );
    }
    console.warn(`[mailer] EMAIL (fallback log after ${mode} error) → ${to}\nSubject: ${subject}\n${text}`);
    return { sent: false, mode: 'log', error: error?.message };
  }
}

export function maskEmail(email) {
  const [local = '', domain = ''] = String(email).split('@');
  const visible = local.slice(0, Math.min(2, local.length));
  return `${visible}${'*'.repeat(Math.max(local.length - visible.length, 1))}@${domain}`;
}
