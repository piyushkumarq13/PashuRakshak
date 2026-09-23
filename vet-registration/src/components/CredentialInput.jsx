/**
 * PIN / password input with a toggle to switch between the two formats.
 * (The credential type the vet chose at setup is stored server-side; the
 * toggle here only adapts the input formatting/hints.)
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

  return (
    <>
      <div className="label-row">
        <label htmlFor={id}>{label} *</label>
        <button type="button" className="btn-link toggle-btn" onClick={onTogglePinType}>
          Use {isPin ? 'password' : 'PIN'} instead
        </button>
      </div>
      <input
        id={id}
        type="password"
        inputMode={isPin ? 'numeric' : 'text'}
        autoComplete={isPin ? 'one-time-code' : 'new-password'}
        maxLength={isPin ? 6 : 64}
        placeholder={isPin ? '••••••' : 'At least 6 characters'}
        value={value}
        onChange={(e) =>
          onChange(isPin ? e.target.value.replace(/\D/g, '').slice(0, 6) : e.target.value)
        }
        autoFocus
      />
      <p className="muted">
        {isPin ? 'Exactly 6 digits.' : 'At least 6 characters.'} You can switch any time.
      </p>

      {onConfirmChange && (
        <>
          <label htmlFor={`${id}-confirm`}>{confirmLabel ?? 'Confirm credential'} *</label>
          <input
            id={`${id}-confirm`}
            type="password"
            inputMode={isPin ? 'numeric' : 'text'}
            autoComplete="new-password"
            maxLength={isPin ? 6 : 64}
            placeholder={isPin ? '••••••' : 'Repeat it'}
            value={confirmValue ?? ''}
            onChange={(e) =>
              onConfirmChange(
                isPin ? e.target.value.replace(/\D/g, '').slice(0, 6) : e.target.value,
              )
            }
          />
        </>
      )}
    </>
  )
}
