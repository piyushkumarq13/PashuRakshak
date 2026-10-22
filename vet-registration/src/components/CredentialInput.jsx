import { useState } from 'react'
import Icon from './Icon'

/**
 * PIN / password input with a toggle to switch between the two formats.
 * The credential type is stored server-side; this toggle only adapts the
 * input formatting and hints.
 */
export default function CredentialInput({
  id,
  label,
  value,
  onChange,
  pinType,
  onTogglePinType,
  confirmValue,
  onConfirmChange,
  confirmLabel,
}) {
  const isPin = pinType === 'pin'
  const [visible, setVisible] = useState(false)

  return (
    <>
      <div className="label-row">
        <label htmlFor={id}>{label}</label>
        <button type="button" className="text-button label-action" onClick={onTogglePinType}>
          Use {isPin ? 'password' : 'PIN'} instead
        </button>
      </div>

      <div className="secure-field">
        <span className="secure-field-icon">
          <Icon name={isPin ? 'pin' : 'lock'} size={16} />
        </span>
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          inputMode={isPin ? 'numeric' : 'text'}
          autoComplete={isPin ? 'off' : 'new-password'}
          maxLength={isPin ? 6 : 64}
          placeholder={isPin ? '6-digit PIN' : 'At least 6 characters'}
          value={value}
          onChange={(e) =>
            onChange(isPin ? e.target.value.replace(/\D/g, '').slice(0, 6) : e.target.value)
          }
          autoFocus
        />
        <button
          type="button"
          className="field-visibility"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide credential' : 'Show credential'}
        >
          <Icon name={visible ? 'eyeOff' : 'eye'} size={16} />
        </button>
      </div>
      <p className="field-help">
        {isPin ? 'Exactly 6 digits.' : 'At least 6 characters.'} You can switch any time.
      </p>

      {onConfirmChange && (
        <>
          <label htmlFor={`${id}-confirm`}>{confirmLabel ?? 'Confirm credential'}</label>
          <div className="secure-field">
            <span className="secure-field-icon">
              <Icon name="check" size={16} />
            </span>
            <input
              id={`${id}-confirm`}
              type={visible ? 'text' : 'password'}
              inputMode={isPin ? 'numeric' : 'text'}
              autoComplete="new-password"
              maxLength={isPin ? 6 : 64}
              placeholder={isPin ? 'Re-enter 6-digit PIN' : 'Repeat your password'}
              value={confirmValue ?? ''}
              onChange={(e) =>
                onConfirmChange(
                  isPin ? e.target.value.replace(/\D/g, '').slice(0, 6) : e.target.value,
                )
              }
            />
          </div>
        </>
      )}
    </>
  )
}
