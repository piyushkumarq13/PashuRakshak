import nodemailer from 'nodemailer';
import { env } from '../../config/env.js';

/**
 * Email delivery for OTPs.
 *
 * MAIL_MODE=smtp → sends via the configured SMTP server (Gmail app password).
 * MAIL_MODE=log  → prints the email to the server console instead (development).
 * If MAIL_MODE=smtp but SMTP settings are missing, falls back to logging so
 * the flow still works and the problem is visible in the logs.
 */

let transporter = null;

function getTransporter() {
  if (env.mailMode !== 'smtp') return null;
  if (!env.smtpHost || !env.smtpUser || !env.smtpPass) {
    console.warn(
      '[mailer] MAIL_MODE=smtp but SMTP_HOST/SMTP_USER/SMTP_PASS are not all set — falling back to console logging.',
    );
    return null;
  }
  if (!transporter) {
    transporter = nodemailer.createTransport({
      host: env.smtpHost,
      port: env.smtpPort,
      secure: env.smtpPort === 465,
      auth: { user: env.smtpUser, pass: env.smtpPass },
    });
  }
  return transporter;
}

/** Sends { to, subject, text, html }. Never throws — returns { sent, mode }. */
export async function sendMail({ to, subject, text, html }) {
  const transport = getTransporter();

  if (!transport) {
    console.warn(`[mailer] EMAIL (log mode) → ${to}\nSubject: ${subject}\n${text}`);
    return { sent: false, mode: 'log' };
  }

  try {
    await transport.sendMail({
      from: env.mailFrom || env.smtpUser,
      to,
      subject,
      text,
      html: html ?? undefined,
    });
    return { sent: true, mode: 'smtp' };
  } catch (error) {
    // Never fail the whole request because email delivery broke — log loudly
    // (the OTP is visible in the log so the flow can still be completed in dev).
    console.error(`[mailer] send to ${to} failed:`, error?.message ?? error);
    console.warn(`[mailer] EMAIL (fallback log after SMTP error) → ${to}\nSubject: ${subject}\n${text}`);
    return { sent: false, mode: 'log', error: error?.message };
  }
}

export function maskEmail(email) {
  const [local = '', domain = ''] = String(email).split('@');
  const visible = local.slice(0, Math.min(2, local.length));
  return `${visible}${'*'.repeat(Math.max(local.length - visible.length, 1))}@${domain}`;
}
