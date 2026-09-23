import { useState, useEffect, useRef } from 'react'
import { govRequestOtp, govVerifyOtp } from '../api'

/**
 * Two-step magistrate login: email → 6-digit OTP → session.
 * (Accounts are seeded via POST /api/v1/core/admin/magistrates.)
 */
export default function LoginScreen({ onLoggedIn }) {
  const [step, setStep] = useState('email') // email | otp
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
    const trimmed = email.trim().toLowerCase()
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      return setError('Enter a valid email address.')
    }
    setLoading(true)
    const { status, data } = await govRequestOtp(trimmed)
    setLoading(false)
    if (status === 200) {
      setInfo(data.message ?? `A 6-digit code was sent to ${data.email ?? trimmed}.`)
      setStep('otp')
      startCooldown(data.cooldownSeconds ?? 60)
    } else {
      setError(data.message || 'Could not send the code.')
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
      setError(data.message || 'Incorrect or expired code.')
    }
  }

  if (step === 'otp') {
    return (
      <div className="card login-card">
        <h2>Enter the code</h2>
        {info && <p className="info">{info}</p>}
        <form onSubmit={verify}>
          <label htmlFor="gov-otp">6-digit code</label>
          <input
            id="gov-otp"
            type="text"
            inputMode="numeric"
            maxLength={6}
            placeholder="------"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            autoFocus
          />
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={loading || code.length !== 6}>
            {loading ? 'Verifying…' : 'Sign in'}
          </button>
        </form>
        <div className="btn-row">
          <button
            type="button"
            className="btn-secondary"
            onClick={sendOtp}
            disabled={loading || cooldown > 0}
          >
            {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
          </button>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => {
              setStep('email')
              setCode('')
              setError('')
            }}
          >
            Change email
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="card login-card">
      <h2>District Magistrate sign in</h2>
      <p className="muted">
        Sign in with your registered email. We’ll send a 6-digit verification code —
        no password needed.
      </p>
      <form onSubmit={sendOtp}>
        <label htmlFor="gov-email">Email address</label>
        <input
          id="gov-email"
          type="email"
          placeholder="dm@example.gov.in"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoFocus
        />
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={loading}>
          {loading ? 'Sending…' : 'Send verification code →'}
        </button>
      </form>
      <p className="muted footnote">
        Don’t have an account? Magistrates are onboarded by the platform admin.
      </p>
    </div>
  )
}
