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
const logModeWarnings = new Set();
let transporter = null;
let brevoSenderCache = null;
const BREVO_SENDER_CACHE_MS = 5 * 60 * 1000;

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

function warnProductionLog(mode) {
  if (logModeWarnings.has(mode)) return;
  logModeWarnings.add(mode);
  console.warn(
    `[mailer] MAIL_MODE=log while NODE_ENV=production — using ${mode} instead because it is configured. ` +
      `Set MAIL_MODE=${mode} explicitly in the environment.`,
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
    case 'log':
      // In production, "log" is almost always an unset/leftover template value.
      // Use any fully configured provider instead of silently logging OTPs.
      if (env.isProduction) {
        if (env.brevoApiKey) {
          warnProductionLog('brevo');
          return 'brevo';
        }
        if (env.resendApiKey) {
          warnProductionLog('resend');
          return 'resend';
        }
        if (smtpConfigured()) {
          warnProductionLog('smtp');
          return 'smtp';
        }
      }
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

function isBrevoKeyFormat() {
  return Boolean(env.brevoApiKey?.startsWith('xkeysib-'));
}

/** Checks the Brevo API key and account status. Returns secrets-free details. */
export async function checkBrevoAccount() {
  if (!env.brevoApiKey) {
    return { ok: false, error: 'BREVO_API_KEY is not set.' };
  }
  if (!isBrevoKeyFormat()) {
    return {
      ok: false,
      error:
        'BREVO_API_KEY does not start with "xkeysib-". ' +
        'Use the Brevo API key, not the SMTP key (xsmtpsib-...).',
    };
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

async function fetchBrevoSenders() {
  if (!isBrevoKeyFormat()) return null;
  if (brevoSenderCache && Date.now() - brevoSenderCache.at < BREVO_SENDER_CACHE_MS) {
    return brevoSenderCache.senders;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HTTP_TIMEOUT_MS);
  try {
    const response = await fetch(`${BREVO_API}/senders`, {
      headers: { 'api-key': env.brevoApiKey, accept: 'application/json' },
      signal: controller.signal,
    });
    if (!response.ok) return null;
    const data = await response.json().catch(() => null);
    const list = Array.isArray(data) ? data : Array.isArray(data?.senders) ? data.senders : [];
    const senders = list
      .filter((sender) => sender?.email)
      .map((sender) => ({
        name: sender.name || 'PashuRakshak',
        email: cleanEmail(sender.email),
        active: sender.active !== false,
      }));
    brevoSenderCache = { at: Date.now(), senders };
    return senders;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

async function getVerifiedBrevoSender() {
  const senders = await fetchBrevoSenders();
  if (!senders?.length) return null;
  return senders.find((sender) => sender.active) ?? senders[0];
}

export async function listBrevoSenders() {
  if (!env.brevoApiKey) return { ok: false, error: 'BREVO_API_KEY is not set.' };
  if (!isBrevoKeyFormat()) {
    return { ok: false, error: 'BREVO_API_KEY is not a Brevo API key (expected xkeysib-...).' };
  }
  const senders = await fetchBrevoSenders();
  if (!senders) {
    return { ok: false, error: 'Could not read Brevo senders (check the API key permissions).' };
  }
  return {
    ok: true,
    senders: senders.map((sender) => ({ ...sender, email: maskEmail(sender.email) })),
  };
}

export function classifyMailError(message) {
  const text = String(message ?? '').toLowerCase();
  if (
    text.includes('unrecognised ip') ||
    text.includes('unauthorized ip') ||
    text.includes('authorised_ips') ||
    text.includes('authorized_ips') ||
    text.includes('ip address')
  ) {
    return 'ip_not_allowed';
  }
  if (
    text.includes('key not found') ||
    text.includes('unauthorized') ||
    text.includes('http 401') ||
    text.includes('must be a brevo api key')
  ) {
    return 'invalid_api_key';
  }
  if (
    text.includes('sender') &&
    (text.includes('not valid') ||
      text.includes('invalid') ||
      text.includes('verify') ||
      text.includes('not verified'))
  ) {
    return 'sender_not_verified';
  }
  if (text.includes('not activated') || (text.includes('account') && text.includes('activat'))) {
    return 'account_not_activated';
  }
  if (text.includes('timeout') || text.includes('aborted')) return 'provider_timeout';
  if (text.includes('http 429') || text.includes('rate limit')) return 'rate_limited';
  return 'provider_error';
}

export function mailErrorHint(reason) {
  switch (reason) {
    case 'invalid_api_key':
      return 'Brevo rejected the API key. Use the Brevo API key that starts with "xkeysib-", not the SMTP key.';
    case 'ip_not_allowed':
      return 'Brevo is blocking the server IP. Disable "Authorized IPs" in Brevo or allow Render outbound IPs.';
    case 'sender_not_verified':
      return 'Brevo rejected the sender address. Verify MAIL_FROM under Senders in Brevo.';
    case 'account_not_activated':
      return 'Complete Brevo account activation, then retry.';
    case 'provider_timeout':
      return 'The email provider timed out. Try again in a moment.';
    case 'rate_limited':
      return 'The email provider rate limit was reached. Try again later.';
    default:
      return 'Check the server email configuration and Render logs.';
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

async function postBrevoEmail(from, payload) {
  // Retry once on rate-limit / temporary Brevo failures.
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    try {
      await postJson(
        `${BREVO_API}/smtp/email`,
        { 'api-key': env.brevoApiKey, accept: 'application/json' },
        { ...payload, sender: { name: from.name, email: from.email } },
      );
      return;
    } catch (error) {
      const retryable = /^HTTP (429|5\d\d):/.test(error?.message ?? '');
      if (attempt === 2 || !retryable) throw error;
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }
}

function isBrevoSenderError(error) {
  const text = String(error?.message ?? '').toLowerCase();
  return (
    text.includes('sender') &&
    (text.includes('not valid') ||
      text.includes('invalid') ||
      text.includes('verify') ||
      text.includes('not verified'))
  );
}

async function sendViaBrevo({ to, subject, text, html }) {
  if (!env.brevoApiKey) throw new Error('BREVO_API_KEY is not set.');
  if (!isBrevoKeyFormat()) {
    throw new Error(
      'BREVO_API_KEY must be a Brevo API key starting with "xkeysib-" (not an SMTP key).',
    );
  }

  let from = parseFrom(env.mailFrom, env.smtpUser);
  if (!from.email) {
    from = await getVerifiedBrevoSender();
    if (from) {
      console.warn(
        `[mailer] MAIL_FROM/SMTP_USER is missing — using verified Brevo sender ${maskEmail(from.email)}.`,
      );
    }
  }
  if (!from?.email) {
    throw new Error(
      'MAIL_FROM (or SMTP_USER) is required for Brevo, and no verified sender was found.',
    );
  }

  const payload = {
    to: [{ email: to }],
    subject,
    textContent: text,
    ...(html ? { htmlContent: html } : {}),
  };

  try {
    await postBrevoEmail(from, payload);
    return;
  } catch (error) {
    if (!isBrevoSenderError(error)) throw error;

    // The configured sender is not verified. If the Brevo account has a
    // verified sender, use it automatically so OTP delivery still works.
    const fallback = await getVerifiedBrevoSender();
    if (!fallback || fallback.email.toLowerCase() === from.email.toLowerCase()) {
      throw error;
    }
    console.warn(
      `[mailer] Brevo rejected sender ${maskEmail(from.email)} — retrying with verified ` +
        `sender ${maskEmail(fallback.email)}. Update MAIL_FROM to ${maskEmail(fallback.email)}.`,
    );
    await postBrevoEmail(fallback, payload);
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
    if (env.isProduction) {
      console.warn(
        `[mailer] EMAIL delivery failed after ${mode} error → ${maskEmail(to)} ` +
          `(subject: ${subject}); message body not logged in production`,
      );
    } else {
      console.warn(
        `[mailer] EMAIL (fallback log after ${mode} error) → ${maskEmail(to)}\nSubject: ${subject}\n${text}`,
      );
    }
    return { sent: false, mode, error: error?.message, reason: classifyMailError(error?.message) };
  }
}

export function maskEmail(email) {
  const [local = '', domain = ''] = String(email).split('@');
  const visible = local.slice(0, Math.min(2, local.length));
  return `${visible}${'*'.repeat(Math.max(local.length - visible.length, 1))}@${domain}`;
}
