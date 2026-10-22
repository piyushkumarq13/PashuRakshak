import { useState, useEffect, useRef } from 'react'
import { govRequestOtp, govVerifyOtp } from '../api'
import Icon from './Icon'

function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

export default function LoginScreen({ onLoggedIn }) {
  const [step, setStep] = useState('email')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [cooldown, setCooldown] = useState(0)
  const timerRef = useRef(null)

  useEffect(() => () => clearInterval(timerRef.current), [])

  function startCooldown(seconds) {
    setCooldown(seconds)
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(() => {
      setCooldown((s) => {
        if (s <= 1) {
          clearInterval(timerRef.current)
          return 0
        }
        return s - 1
      })
    }, 1000)
  }

  async function sendOtp(e) {
    e?.preventDefault()
    setError('')
    setInfo('')
    const trimmed = email.trim().toLowerCase()
    if (!isValidEmail(trimmed)) {
      setError('Enter your registered email address.')
      return
    }
    setLoading(true)
    const { status, data } = await govRequestOtp(trimmed)
    setLoading(false)
    if (status === 200) {
      setInfo(data.message ?? `A 6-digit code was sent to ${data.email ?? trimmed}.`)
      setStep('otp')
      startCooldown(data.cooldownSeconds ?? 60)
    } else {
      setError(data.message || 'Could not send the code. Please try again.')
      if (data.retryAfterSeconds) startCooldown(data.retryAfterSeconds)
    }
  }

  async function verify(e) {
    e.preventDefault()
    if (code.length !== 6) return
    setError('')
    setLoading(true)
    const { status, data } = await govVerifyOtp(email.trim().toLowerCase(), code)
    setLoading(false)
    if (status === 200 && data.token) {
      onLoggedIn(data)
    } else {
      setError(data.message || 'Incorrect or expired code. Request a new one.')
    }
  }

  function changeEmail() {
    setStep('email')
    setCode('')
    setError('')
    setInfo('')
  }

  return (
    <div className="login-card">
      <div className="login-heading">
        <span className="login-icon">
          <Icon name={step === 'otp' ? 'mail' : 'shield'} size={22} />
        </span>
        <div>
          <p className="eyebrow">{step === 'otp' ? 'One final step' : 'Authorised personnel'}</p>
          <h2>{step === 'otp' ? 'Check your inbox' : 'Sign in to your district'}</h2>
        </div>
      </div>

      <div className="login-steps" aria-label="Sign in progress">
        <span className={step === 'email' ? 'is-current' : 'is-complete'}>
          <i>1</i> Email
        </span>
        <span className="step-line" aria-hidden="true" />
        <span className={step === 'otp' ? 'is-current' : ''}>
          <i>2</i> Verify
        </span>
      </div>

      {step === 'email' ? (
        <>
          <p className="login-lead">
            Use the email address registered by the platform administrator. We will send a
            one-time verification code, so there is no password to remember.
          </p>
          <form onSubmit={sendOtp} className="stack-form">
            <label htmlFor="gov-email">Official email address</label>
            <div className="input-shell">
              <Icon name="mail" size={17} />
              <input
                id="gov-email"
                type="email"
                autoComplete="email"
                placeholder="dm@example.gov.in"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoFocus
              />
            </div>
            {error && (
              <p className="form-message form-message-error" role="alert">
                <Icon name="close" size={15} />
                {error}
              </p>
            )}
            <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
              {loading ? <span className="spinner" /> : <Icon name="arrowRight" size={17} />}
              {loading ? 'Sending secure code' : 'Continue with email'}
            </button>
          </form>
        </>
      ) : (
        <>
          <p className="login-lead">
            Enter the 6-digit code sent to <strong>{email}</strong>. The code expires shortly
            for your security.
          </p>
          {info && (
            <p className="form-message form-message-info">
              <Icon name="mail" size={15} />
              {info}
            </p>
          )}
          <form onSubmit={verify} className="stack-form">
            <label htmlFor="gov-otp">Verification code</label>
            <input
              id="gov-otp"
              className="otp-input"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              placeholder="000000"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              autoFocus
            />
            {error && (
              <p className="form-message form-message-error" role="alert">
                <Icon name="close" size={15} />
                {error}
              </p>
            )}
            <button
              className="btn btn-primary btn-block"
              type="submit"
              disabled={loading || code.length !== 6}
            >
              {loading ? <span className="spinner" /> : <Icon name="shield" size={17} />}
              {loading ? 'Verifying' : 'Verify and sign in'}
            </button>
          </form>
          <div className="login-actions">
            <button
              type="button"
              className="text-button"
              onClick={sendOtp}
              disabled={loading || cooldown > 0}
            >
              <Icon name="refresh" size={14} />
              {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
            </button>
            <button type="button" className="text-button" onClick={changeEmail}>
              Change email
            </button>
          </div>
        </>
      )}

      <p className="login-footnote">
        <Icon name="shield" size={14} />
        Accounts are provisioned by the PashuRakshak platform administrator.
      </p>
    </div>
  )
}
