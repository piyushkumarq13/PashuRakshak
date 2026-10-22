import { Router } from 'express';
import { env } from '../../config/env.js';
import { adminSecret } from '../middleware/adminSecret.js';
import {
  checkBrevoAccount,
  getMailerStatus,
  listBrevoSenders,
  sendMail,
} from '../services/mailer.js';

const router = Router();

/**
 * GET /api/v1/core/admin/mail
 * Secret-free email diagnostics for the deployed server.
 * Requires X-Admin-Secret.
 */
router.get('/admin/mail', adminSecret, async (_req, res) => {
  const status = getMailerStatus();
  const [brevoAccount, brevoSenders] = status.brevoConfigured
    ? await Promise.all([checkBrevoAccount(), listBrevoSenders()])
    : [null, null];
  const runtime = {
    nodeEnv: env.nodeEnv,
    renderServiceName: process.env.RENDER_SERVICE_NAME ?? null,
    renderServiceId: process.env.RENDER_SERVICE_ID ?? null,
    renderCommit: process.env.RENDER_GIT_COMMIT ?? null,
    renderExternalUrl: process.env.RENDER_EXTERNAL_URL ?? null,
    loadedEnvFiles: env.loadedEnvFiles,
    smtpHost: env.smtpHost,
    smtpPort: env.smtpPort,
    // Names only — never values. Makes typos like BREVO_KEY visible.
    mailEnvKeys: Object.keys(process.env)
      .filter((key) => /BREVO|MAIL|SMTP|RESEND|SENDINBLUE/i.test(key))
      .sort(),
  };
  return res.status(200).json({ ...status, runtime, brevoAccount, brevoSenders });
});

/**
 * POST /api/v1/core/admin/mail/test
 * Body: { to }
 * Sends a test email through the configured provider and returns the exact
 * error when it fails. Requires X-Admin-Secret.
 */
router.post('/admin/mail/test', adminSecret, async (req, res) => {
  const to = String(req.body?.to ?? '').trim();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) {
    return res.status(400).json({
      error: 'invalid_email',
      message: 'Body must include a valid "to" email address.',
    });
  }

  const result = await sendMail({
    to,
    subject: 'PashuRakshak email delivery test',
    text: 'If you received this email, PashuRakshak email delivery is working.',
    html: '<p>If you received this email, <strong>PashuRakshak email delivery is working.</strong></p>',
  });

  return res.status(result.sent ? 200 : 502).json({
    success: result.sent,
    mode: result.mode,
    ...(result.reason ? { reason: result.reason } : {}),
    ...(result.error ? { error: result.error } : {}),
  });
});

export default router;
