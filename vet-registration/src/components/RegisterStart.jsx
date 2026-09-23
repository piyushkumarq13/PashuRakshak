import { useState } from 'react'

/**
 * Registration step 1 — phone number + email.
 * The OTP goes to the email (SMS is not used); the phone becomes the vet
 * app login identifier.
 */
export default function RegisterStart({ onSubmit, onBack }) {
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')

  function handleSubmit(e) {
    e.preventDefault()
    setError('')
    const digits = phone.replace(/\D/g, '')
    if (digits.length < 10) return setError('Enter a valid 10-digit phone number.')
    const trimmed = email.trim().toLowerCase()
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) return setError('Enter a valid email address.')
    onSubmit(digits, trimmed)
  }

  return (
    <div className="card">
      <h2>Start your registration</h2>
      <p className="muted">
        Veterinarians only. We’ll email you a 6-digit verification code — no SMS needed.
      </p>
      <form onSubmit={handleSubmit}>
        <label htmlFor="reg-phone">Phone number *</label>
        <div className="phone-row">
          <span className="prefix">+91</span>
          <input
            id="reg-phone"
            type="tel"
            inputMode="tel"
            placeholder="98765 43210"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            autoFocus
          />
        </div>

        <label htmlFor="reg-email">Email address *</label>
        <input
          id="reg-email"
          type="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        {error && <p className="error">{error}</p>}
        <button type="submit">Send verification code →</button>
      </form>

      <div className="btn-row">
        <button type="button" className="btn-secondary" onClick={onBack}>
          Back to sign in
        </button>
      </div>
    </div>
  )
}
