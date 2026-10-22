import { useState } from 'react'
import Icon from './Icon'

/**
 * Registration step 1: phone number + email.
 * The OTP goes to email; the phone becomes the vet app login identifier.
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
      <div className="progress-header">
        <span className="progress-step is-current">
          <i>1</i> Account
        </span>
        <span className="progress-line" />
        <span className="progress-step">
          <i>2</i> Verify
        </span>
        <span className="progress-line" />
        <span className="progress-step">
          <i>3</i> Credential
        </span>
      </div>

      <div className="card-head">
        <span className="card-icon">
          <Icon name="user" size={21} />
        </span>
        <div>
          <p className="eyebrow">Create your account</p>
          <h2>Start your registration</h2>
          <p>Veterinarians only. We will email a six-digit verification code.</p>
        </div>
      </div>

      <form className="stack-form" onSubmit={handleSubmit}>
        <label htmlFor="reg-phone">Phone number</label>
        <div className="phone-field">
          <span className="phone-prefix">+91</span>
          <input
            id="reg-phone"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            placeholder="98765 43210"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            autoFocus
          />
        </div>
        <p className="field-help">This becomes your login ID for the PashuRakshak Vet App.</p>

        <label htmlFor="reg-email">Email address</label>
        <div className="secure-field">
          <span className="secure-field-icon">
            <Icon name="mail" size={16} />
          </span>
          <input
            id="reg-email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <p className="field-help">Your verification code and review updates are sent here.</p>

        {error && (
          <p className="form-message form-message-error" role="alert">
            <Icon name="alert" size={15} /> {error}
          </p>
        )}

        <button className="btn btn-primary btn-block" type="submit">
          Send verification code <Icon name="arrowRight" size={16} />
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
