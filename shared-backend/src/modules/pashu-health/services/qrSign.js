import crypto from 'node:crypto';

/**
 * Server mirror of the Android QrPayloads signer: payload is
 * "<id>|<timestampMillis>|<hmacHex>" and the signature is HMAC-SHA256 of
 * "<id>|<timestampMillis>" keyed by the app API key (the same key the
 * Android client signed with).
 */
export function signQrPayload(id, secret) {
  const ts = Date.now();
  return `${id}|${ts}|${hmacHex(`${id}|${ts}`, secret)}`;
}

export function verifyAndExtractId(raw, secret) {
  if (typeof raw !== 'string') return null;
  const parts = raw.split('|');
  if (parts.length !== 3) return null;
  const [id, ts, sig] = parts;
  if (!/^[0-9a-f]{64}$/.test(sig)) return null;
  const expected = hmacHex(`${id}|${ts}`, secret);
  const a = Buffer.from(sig, 'hex');
  const b = Buffer.from(expected, 'hex');
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  return id;
}

function hmacHex(data, secret) {
  return crypto.createHmac('sha256', secret).update(data).digest('hex');
}
