import { useState } from 'react'
import { vetResetPin, setToken } from '../api'
import CredentialInput from './CredentialInput'
import Icon from './Icon'

/**
 * Forgot flow final step: choose a new PIN/password after the reset code
 * was verified, then sign the vet in.
 */
export default function ResetCredential({ verifyToken, onDone, onBack }) {
  const [pinType, setPinType] = useState('pin')
  const [credential, setCredential] = useState('')
  const [confirm, setConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (pinType === 'pin' && credential.length !== 6) {
      return setError('PIN must be exactly 6 digits.')
    }
    if (pinType === 'password' && credential.length < 6) {
      return setError('Password must be at least 6 characters.')
    }
    if (credential !== confirm) return setError('Both fields must match.')

    setLoading(true)
    const { status, data } = await vetResetPin(verifyToken, credential, pinType)
    setLoading(false)
    if (status === 200 && data.token) {
      setToken(data.token)
      onDone(data)
    } else {
      setError(data.message || 'Could not reset the credential.')
    }
  }

  return (
    <div className="card">
      <div className="card-head">
        <span className="card-icon">
          <Icon name="shield" size={21} />
        </span>
        <div>
          <p className="eyebrow">Account recovery</p>
          <h2>Choose a new credential</h2>
          <p>Your reset code is verified. Pick a new way to sign in.</p>
        </div>
      </div>

      <form className="stack-form" onSubmit={handleSubmit}>
        <CredentialInput
          id="reset-credential"
          label={pinType === 'pin' ? 'New six-digit PIN' : 'New password'}
          value={credential}
          onChange={setCredential}
          pinType={pinType}
          onTogglePinType={() => {
            setPinType((t) => (t === 'pin' ? 'password' : 'pin'))
            setCredential('')
            setConfirm('')
          }}
          confirmValue={confirm}
          onConfirmChange={setConfirm}
          confirmLabel={`Confirm ${pinType === 'pin' ? 'PIN' : 'password'}`}
        />

        {error && (
          <p className="form-message form-message-error" role="alert">
            <Icon name="alert" size={15} /> {error}
          </p>
        )}

        <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
          {loading ? (
            <>
              <span className="spinner" /> Saving credential...
            </>
          ) : (
            <>
              Save and sign in <Icon name="arrowRight" size={16} />
            </>
          )}
        </button>
      </form>

      <div className="card-actions single">
        <button type="button" className="text-button" onClick={onBack}>
          <Icon name="arrowRight" size={14} className="rotate-180" /> Back
        </button>
      </div>
    </div>
  )
}
