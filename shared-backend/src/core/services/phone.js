/**
 * Phone-number canonicalization.
 *
 * All phones are stored and looked up as canonical digits:
 *   "+91 98765-43210" → "9876543210"
 *   "09876543210"     → "9876543210"
 *   "919876543210"    → "9876543210"
 * (Migration 007 normalized every existing row the same way.)
 */
export function canonicalPhone(input) {
  let digits = String(input ?? '').replace(/\D/g, '');
  if (digits.length === 12 && digits.startsWith('91')) digits = digits.slice(2);
  else if (digits.length === 11 && digits.startsWith('0')) digits = digits.slice(1);
  return digits;
}

/** True when the input normalizes to a plausible 10-digit Indian mobile number. */
export function isValidPhone(input) {
  return /^\d{10}$/.test(canonicalPhone(input));
}
