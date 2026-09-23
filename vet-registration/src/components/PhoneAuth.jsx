import { useState } from 'react'
import { signInWithPhoneNumber } from 'firebase/auth'
import { auth, getRecaptchaVerifier, resetRecaptcha } from '../firebase'

export default function PhoneAuth({ onSignedIn }) {
  const [phone, setPhone] = useState('')
  const [otp, setOtp] = useState('')
  const [confirmation, setConfirmation] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')

  async function handleSendOtp(e) {
    e.preventDefault()
    setError('')
    setInfo('')
    const digits = phone.replace(/\D/g, '')
    if (digits.length < 10) {
      setError('Enter a valid phone number (at least 10 digits).')
      return
    }
    const e164 = digits.startsWith('91') ? `+${digits}` : `+91${digits}`
    setLoading(true)
    try {
      resetRecaptcha()
      const verifier = getRecaptchaVerifier()
      const result = await signInWithPhoneNumber(auth, e164, verifier)
      setConfirmation(result)
      setInfo(`OTP sent to ${e164}`)
    } catch (err) {
      console.error(err)
      setError(err.message || 'Failed to send OTP. Try again.')
      resetRecaptcha()
    } finally {
      setLoading(false)
    }
  }

  async function handleVerifyOtp(e) {
    e.preventDefault()
    setError('')
    if (!confirmation) return
    setLoading(true)
    try {
      const result = await confirmation.confirm(otp.trim())
      const token = await result.user.getIdToken()
      onSignedIn(token)
    } catch (err) {
      console.error(err)
      setError('Invalid OTP. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  if (confirmation) {
    return (
      <div className="card">
        <h2>Enter OTP</h2>
        {info && <p className="info">{info}</p>}
        <form onSubmit={handleVerifyOtp}>
          <label htmlFor="otp">6-digit code</label>
          <input
            id="otp"
            type="text"
            inputMode="numeric"
            maxLength={6}
            placeholder="------"
            value={otp}
            onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
            autoFocus
          />
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={loading || otp.length !== 6}>
            {loading ? 'Verifying…' : 'Verify & Continue'}
          </button>
        </form>
        <button
          className="btn-link"
          onClick={() => {
            setConfirmation(null)
            setOtp('')
            setError('')
            setInfo('')
            resetRecaptcha()
          }}
        >
          Change phone number
        </button>
      </div>
    )
  }

  return (
    <div className="card">
      <h2>Sign in with your phone</h2>
      <p className="muted">Veterinarians only. Farmers use the PashuRakshak app.</p>
      <form onSubmit={handleSendOtp}>
        <label htmlFor="phone">Phone number</label>
        <div className="phone-row">
          <span className="prefix">+91</span>
          <input
            id="phone"
            type="tel"
            inputMode="tel"
            placeholder="98765 43210"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            autoFocus
          />
        </div>
        {error && <p className="error">{error}</p>}
        {info && <p className="info">{info}</p>}
        <button type="submit" disabled={loading || phone.replace(/\D/g, '').length < 10}>
          {loading ? 'Sending…' : 'Send OTP'}
        </button>
      </form>
    </div>
  )
}
