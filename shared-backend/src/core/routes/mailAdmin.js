import { Router } from 'express';
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
  const brevoAccount = status.brevoConfigured ? await checkBrevoAccount() : null;
  const brevoSenders = status.brevoConfigured ? await listBrevoSenders() : null;
  return res.status(200).json({ ...status, brevoAccount, brevoSenders });
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
