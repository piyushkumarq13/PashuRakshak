import { useState } from 'react'
import { sendOtp } from '../api'
import Icon from './Icon'

/**
 * Forgot PIN/password step 1: enter the registered email so a reset code
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
      <div className="card-head">
        <span className="card-icon">
          <Icon name="lock" size={21} />
        </span>
        <div>
          <p className="eyebrow">Account recovery</p>
          <h2>Reset your credential</h2>
          <p>Enter the email linked to your veterinary application.</p>
        </div>
      </div>

      <form className="stack-form" onSubmit={handleSubmit}>
        <label htmlFor="forgot-email">Registered email</label>
        <div className="secure-field">
          <span className="secure-field-icon">
            <Icon name="mail" size={16} />
          </span>
          <input
            id="forgot-email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoFocus
          />
        </div>
        <p className="field-help">We will email a six-digit reset code to this address.</p>

        {error && (
          <p className="form-message form-message-error" role="alert">
            <Icon name="alert" size={15} /> {error}
          </p>
        )}

        <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
          {loading ? (
            <>
              <span className="spinner" /> Sending code...
            </>
          ) : (
            <>
              Send reset code <Icon name="arrowRight" size={16} />
            </>
          )}
        </button>
      </form>

      <div className="card-actions single">
        <button type="button" className="text-button" onClick={onBack}>
          <Icon name="arrowRight" size={14} className="rotate-180" /> Back to sign in
        </button>
      </div>
    </div>
  )
}
