import { useEffect, useRef, useState } from 'react'
import { sendOtp, verifyOtp } from '../api'
import Icon from './Icon'

/**
 * Email OTP step: auto-sends a six-digit code on mount and verifies it.
 * Calls onVerified(verifyToken) on success.
 */
export default function OtpVerify({ email, purpose, phone, heading, onVerified, onBack }) {
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [sending, setSending] = useState(true)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [cooldown, setCooldown] = useState(0)
  const sentRef = useRef(false)
  const timerRef = useRef(null)

  async function send() {
    setError('')
    setSending(true)
    const { status, data } = await sendOtp(email, purpose, phone)
    setSending(false)
    if (status === 200) {
      setInfo(`A six-digit code was sent to ${data.email ?? email}.`)
      const seconds = data.cooldownSeconds ?? 60
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
    } else {
      setError(data.message || 'Could not send the code. Try again.')
      if (data.retryAfterSeconds) setCooldown(data.retryAfterSeconds)
    }
  }

  useEffect(() => {
    if (sentRef.current) return
    sentRef.current = true
    send()
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleVerify(e) {
    e.preventDefault()
    if (code.length !== 6) return
    setError('')
    setLoading(true)
    const { status, data } = await verifyOtp(email, purpose, code)
    setLoading(false)
    if (status === 200) {
      onVerified(data.verifyToken)
    } else {
      setError(data.message || 'Incorrect code. Try again.')
    }
  }

  return (
    <div className="card">
      <div className="progress-header">
        <span className="progress-step is-complete">
          <i>
            <Icon name="check" size={11} />
          </i>{' '}
          Account
        </span>
        <span className="progress-line is-complete" />
        <span className="progress-step is-current">
          <i>2</i> Verify
        </span>
        <span className="progress-line" />
        <span className="progress-step">
          <i>3</i> Credential
        </span>
      </div>

      <div className="card-head">
        <span className="card-icon">
          <Icon name="mail" size={21} />
        </span>
        <div>
          <p className="eyebrow">Email verification</p>
          <h2>{heading ?? 'Enter the code'}</h2>
          <p>
            We sent a code to <strong>{email}</strong>.
          </p>
        </div>
      </div>

      {info && (
        <p className="form-message form-message-info">
          <Icon name="info" size={15} /> {info}
        </p>
      )}

      <form className="stack-form otp-form" onSubmit={handleVerify}>
        <label htmlFor="otp">Six-digit code</label>
        <input
          id="otp"
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
            <Icon name="alert" size={15} /> {error}
          </p>
        )}

        <button
          className="btn btn-primary btn-block"
          type="submit"
          disabled={loading || code.length !== 6}
        >
          {loading ? (
            <>
              <span className="spinner" /> Verifying...
            </>
          ) : (
            <>
              Verify email <Icon name="arrowRight" size={16} />
            </>
          )}
        </button>
      </form>

      <div className="card-actions">
        <button
          type="button"
          className="text-button"
          onClick={send}
          disabled={sending || cooldown > 0}
        >
          <Icon name="refresh" size={14} />
          {cooldown > 0 ? `Resend in ${cooldown}s` : sending ? 'Sending...' : 'Resend code'}
        </button>
        <button type="button" className="text-button" onClick={onBack}>
          <Icon name="arrowRight" size={14} className="rotate-180" /> Back
        </button>
      </div>

      <div className="security-note">
        <Icon name="shield" size={14} />
        Never share this code. PashuRakshak will never ask for it.
      </div>
    </div>
  )
}
