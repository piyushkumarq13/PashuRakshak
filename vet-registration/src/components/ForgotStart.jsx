import { useState } from 'react'
import { sendOtp } from '../api'

/**
 * Forgot PIN/password — step 1: enter the registered email so a reset code
 * can be emailed (purpose vet_reset_pin).
 */
export default function ForgotStart({ onStart, onBack }) {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    const trimmed = email.trim().toLowerCase()
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      return setError('Enter a valid email address.')
    }
    setLoading(true)
    const { status, data } = await sendOtp(trimmed, 'vet_reset_pin')
    setLoading(false)
    if (status === 200) {
      onStart(trimmed)
    } else {
      setError(data.message || 'Could not send the code.')
    }
  }

  return (
    <div className="card">
      <h2>Reset your credential</h2>
      <p className="muted">
        Enter the email you registered with — we’ll email you a 6-digit reset code.
      </p>
      <form onSubmit={handleSubmit}>
        <label htmlFor="forgot-email">Registered email</label>
        <input
          id="forgot-email"
          type="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoFocus
        />
        {error && <p className="error">{error}</p>}
        <div className="btn-row">
          <button type="button" className="btn-secondary" onClick={onBack}>
            Back
          </button>
          <button type="submit" disabled={loading}>
            {loading ? 'Sending…' : 'Send reset code'}
          </button>
        </div>
      </form>
    </div>
  )
}
