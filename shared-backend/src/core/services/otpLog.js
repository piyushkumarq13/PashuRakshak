/**
 * In-memory log of every OTP email attempt (newest first).
 * Powers the debug page at GET /otp while SMTP is being fixed.
 * Not persisted — cleared on restart. Disable the page with OTP_PAGE=false.
 */

const MAX_ENTRIES = 100;
const entries = [];

export function recordOtp({ to, purpose, code, subject, delivery }) {
  entries.unshift({
    id: `${Date.now()}-${entries.length}`,
    at: Date.now(),
    to,
    purpose,
    code,
    subject,
    delivery: {
      sent: Boolean(delivery?.sent),
      mode: delivery?.mode ?? 'unknown',
      error: delivery?.error ?? null,
    },
  });
  if (entries.length > MAX_ENTRIES) {
    entries.length = MAX_ENTRIES;
  }
}

export function listOtps() {
  return entries;
}

export function clearOtps() {
  entries.length = 0;
}
