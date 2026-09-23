import { useState } from 'react'
import { vetLogin, setToken } from '../api'
import CredentialInput from './CredentialInput'

/**
 * Sign in with phone + PIN/password — allowed while the application is
 * pending/rejected so the vet can always check its status. The backend
 * blocks client='app' logins until approval; this web uses client='web'.
 */
export default function SignInForm({ onSignedIn, onGoRegister, onForgot }) {
  const [phone, setPhone] = useState('')
  const [credential, setCredential] = useState('')
  const [pinType, setPinType] = useState('pin')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    const digits = phone.replace(/\D/g, '')
    if (digits.length < 10) return setError('Enter a valid phone number.')
    if (!credential) return setError('Enter your PIN or password.')

    setLoading(true)
    const { status, data } = await vetLogin(digits, credential)
    setLoading(false)

    if (status === 200 && data.token) {
      setToken(data.token)
      onSignedIn(data)
    } else if (status === 403 && data.applicationStatus) {
      // Vet app would be blocked here; the web continues to show status.
      onSignedIn(data)
    } else {
      setError(data.message || 'Could not sign in.')
    }
  }

  return (
    <div className="card">
      <h2>Sign in</h2>
      <p className="muted">Veterinarians — check your application status any time.</p>
      <form onSubmit={handleSubmit}>
        <label htmlFor="si-phone">Phone number</label>
        <div className="phone-row">
          <span className="prefix">+91</span>
          <input
            id="si-phone"
            type="tel"
            inputMode="tel"
            placeholder="98765 43210"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
        </div>

        <CredentialInput
          id="si-credential"
          label={pinType === 'pin' ? 'PIN' : 'Password'}
          value={credential}
          onChange={setCredential}
          pinType={pinType}
          onTogglePinType={() => {
            setPinType((t) => (t === 'pin' ? 'password' : 'pin'))
            setCredential('')
          }}
        />

        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={loading}>
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <div className="btn-row">
        <button type="button" className="btn-secondary" onClick={onForgot}>
          Forgot PIN / password?
        </button>
        <button type="button" className="btn-secondary" onClick={onGoRegister}>
          New vet? Register
        </button>
      </div>
    </div>
  )
}
