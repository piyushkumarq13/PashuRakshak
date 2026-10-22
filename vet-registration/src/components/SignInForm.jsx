import { useState } from 'react'
import { vetLogin, setToken } from '../api'
import CredentialInput from './CredentialInput'
import Icon from './Icon'

/**
 * Sign in with phone + PIN/password. Allowed while the application is
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
      onSignedIn(data)
    } else {
      setError(data.message || 'Could not sign in.')
    }
  }

  return (
    <div className="card auth-card">
      <div className="card-head">
        <span className="card-icon">
          <Icon name="user" size={21} />
        </span>
        <div>
          <p className="eyebrow">Veterinarian access</p>
          <h2>Welcome back</h2>
          <p>Sign in to track your application and district review.</p>
        </div>
      </div>

      <form className="stack-form" onSubmit={handleSubmit}>
        <label htmlFor="si-phone">Phone number</label>
        <div className="phone-field">
          <span className="phone-prefix">+91</span>
          <input
            id="si-phone"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
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

        {error && (
          <p className="form-message form-message-error" role="alert">
            <Icon name="alert" size={15} /> {error}
          </p>
        )}

        <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
          {loading ? (
            <>
              <span className="spinner" /> Signing in...
            </>
          ) : (
            <>
              Sign in <Icon name="arrowRight" size={16} />
            </>
          )}
        </button>
      </form>

      <div className="card-actions">
        <button type="button" className="text-button" onClick={onForgot}>
          <Icon name="lock" size={14} /> Forgot PIN or password?
        </button>
        <button type="button" className="text-button" onClick={onGoRegister}>
          New vet? Create an account <Icon name="arrowRight" size={14} />
        </button>
      </div>

      <div className="security-note">
        <Icon name="shield" size={14} />
        Secure sign-in for verified veterinary professionals
      </div>
    </div>
  )
}
