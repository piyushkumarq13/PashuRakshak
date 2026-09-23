import { useState } from 'react'
import { vetResetPin, setToken } from '../api'
import CredentialInput from './CredentialInput'

/**
 * Forgot flow — final step: choose a new PIN/password after the reset code
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
      <h2>Choose a new credential</h2>
      <p className="muted">Code verified. Pick a new {pinType === 'pin' ? 'PIN' : 'password'}.</p>
      <form onSubmit={handleSubmit}>
        <CredentialInput
          id="reset-credential"
          label={pinType === 'pin' ? 'New 6-digit PIN' : 'New password'}
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
        {error && <p className="error">{error}</p>}
        <div className="btn-row">
          <button type="button" className="btn-secondary" onClick={onBack}>
            Back
          </button>
          <button type="submit" disabled={loading}>
            {loading ? 'Saving…' : 'Save & sign in'}
          </button>
        </div>
      </form>
    </div>
  )
}
