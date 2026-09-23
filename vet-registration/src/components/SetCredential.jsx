import { useState } from 'react'
import CredentialInput from './CredentialInput'

/**
 * Step 2 of registration: the vet chooses a 6-digit PIN or a password.
 * Creates the vet account (email already verified) and signs them in.
 */
export default function SetCredential({ phone, verifyToken, onSubmit, onBack, submitting, error }) {
  const [pinType, setPinType] = useState('pin')
  const [credential, setCredential] = useState('')
  const [confirm, setConfirm] = useState('')
  const [localError, setLocalError] = useState('')

  function handleSubmit(e) {
    e.preventDefault()
    setLocalError('')
    if (pinType === 'pin') {
      if (credential.length !== 6) return setLocalError('PIN must be exactly 6 digits.')
    } else if (credential.length < 6) {
      return setLocalError('Password must be at least 6 characters.')
    }
    if (credential !== confirm) return setLocalError('Both fields must match.')
    onSubmit(credential, pinType)
  }

  return (
    <div className="card">
      <h2>Set your login credential</h2>
      <p className="muted">
        Verified <strong>{phone}</strong>. Choose how you’ll sign in to the PashuRakshak Vet App —
        with your phone number and this {pinType === 'pin' ? '6-digit PIN' : 'password'}.
      </p>
      <form onSubmit={handleSubmit}>
        <CredentialInput
          id="credential"
          label={pinType === 'pin' ? '6-digit PIN' : 'Password'}
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
        {(localError || error) && <p className="error">{localError || error}</p>}
        <div className="btn-row">
          <button type="button" className="btn-secondary" onClick={onBack}>
            Back
          </button>
          <button type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Continue'}
          </button>
        </div>
      </form>
    </div>
  )
}
