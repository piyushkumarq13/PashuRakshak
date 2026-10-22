import { useState } from 'react'
import CredentialInput from './CredentialInput'
import Icon from './Icon'

/**
 * Step 2 of registration: the vet chooses a six-digit PIN or a password.
 * Creates the vet account (email already verified) and signs them in.
 */
export default function SetCredential({ phone, onSubmit, onBack, submitting, error }) {
  const [pinType, setPinType] = useState('pin')
  const [credential, setCredential] = useState('')
  const [confirm, setConfirm] = useState('')
  const [localError, setLocalError] = useState('')

  function changeType(next) {
    if (next === pinType) return
    setPinType(next)
    setCredential('')
    setConfirm('')
    setLocalError('')
  }

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
      <div className="progress-header">
        <span className="progress-step is-complete">
          <i>
            <Icon name="check" size={11} />
          </i>{' '}
          Account
        </span>
        <span className="progress-line is-complete" />
        <span className="progress-step is-complete">
          <i>
            <Icon name="check" size={11} />
          </i>{' '}
          Verify
        </span>
        <span className="progress-line is-complete" />
        <span className="progress-step is-current">
          <i>3</i> Credential
        </span>
      </div>

      <div className="card-head">
        <span className="card-icon">
          <Icon name="lock" size={21} />
        </span>
        <div>
          <p className="eyebrow">Account security</p>
          <h2>Set your login credential</h2>
          <p>
            Verified <strong>{phone}</strong>. Choose how you will sign in to the Vet App.
          </p>
        </div>
      </div>

      <div className="field-block">
        <span className="field-label">Sign-in method</span>
        <div className="segmented" role="group" aria-label="Choose credential type">
          <button
            type="button"
            className={pinType === 'pin' ? 'is-active' : ''}
            onClick={() => changeType('pin')}
          >
            <Icon name="pin" size={14} /> Six-digit PIN
          </button>
          <button
            type="button"
            className={pinType === 'password' ? 'is-active' : ''}
            onClick={() => changeType('password')}
          >
            <Icon name="lock" size={14} /> Password
          </button>
        </div>
      </div>

      <form className="stack-form" onSubmit={handleSubmit}>
        <CredentialInput
          id="credential"
          label={pinType === 'pin' ? 'Six-digit PIN' : 'Password'}
          value={credential}
          onChange={setCredential}
          pinType={pinType}
          onTogglePinType={() => changeType(pinType === 'pin' ? 'password' : 'pin')}
          confirmValue={confirm}
          onConfirmChange={setConfirm}
          confirmLabel={`Confirm ${pinType === 'pin' ? 'PIN' : 'password'}`}
        />

        {(localError || error) && (
          <p className="form-message form-message-error" role="alert">
            <Icon name="alert" size={15} /> {localError || error}
          </p>
        )}

        <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
          {submitting ? (
            <>
              <span className="spinner" /> Saving account...
            </>
          ) : (
            <>
              Continue to application <Icon name="arrowRight" size={16} />
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
