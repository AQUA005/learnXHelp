import { useState } from 'react'
import { Field } from '@/components/ui'
import { Icon } from '@/components/icons'

/**
 * A password box you can look at.
 *
 * Typing a password blind into a phone is how people end up locked out of an
 * account they have the password to. The control is a button rather than a
 * checkbox because it acts immediately, and it says which state it will move
 * to, not which state it is in.
 */
export default function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete,
  error,
  hint,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete: 'current-password' | 'new-password'
  error?: string
  hint?: string
}) {
  const [shown, setShown] = useState(false)

  return (
    <Field label={label} htmlFor={id} error={error}>
      <div className="password-field">
        <input
          id={id}
          type={shown ? 'text' : 'password'}
          value={value}
          autoComplete={autoComplete}
          required
          onChange={(event) => onChange(event.target.value)}
        />
        <button
          type="button"
          className="password-reveal"
          aria-label={shown ? 'Hide password' : 'Show password'}
          aria-pressed={shown}
          onClick={() => setShown((current) => !current)}
        >
          <Icon name={shown ? 'eye-off' : 'eye'} />
        </button>
      </div>
      {hint && <p className="small muted password-hint">{hint}</p>}
    </Field>
  )
}
