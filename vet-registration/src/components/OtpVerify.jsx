import { useEffect, useRef, useState } from 'react'
import { sendOtp, verifyOtp } from '../api'

/**
 * Email OTP step — auto-sends a 6-digit code on mount (for the given email)
 * and verifies it. Calls onVerified(verifyToken) on success.
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

  const send = async () => {
    setError('')
    setSending(true)
    const { status, data } = await sendOtp(email, purpose, phone)
    setSending(false)
    if (status === 200) {
      setInfo(`A 6-digit code was sent to ${data.email ?? email}.`)
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
      <h2>{heading ?? 'Enter the code'}</h2>
      {info && <p className="info">{info}</p>}
      <form onSubmit={handleVerify}>
        <label htmlFor="otp">6-digit code</label>
        <input
          id="otp"
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
          {loading ? 'Verifying…' : 'Verify'}
        </button>
      </form>
      <div className="btn-row">
        <button type="button" className="btn-secondary" onClick={send} disabled={sending || cooldown > 0}>
          {cooldown > 0 ? `Resend in ${cooldown}s` : sending ? 'Sending…' : 'Resend code'}
        </button>
        <button type="button" className="btn-secondary" onClick={onBack}>
          Back
        </button>
      </div>
    </div>
  )
}
