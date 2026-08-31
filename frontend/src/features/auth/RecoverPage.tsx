import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { Alert, Field } from '@/components/ui'
import AuthLayout from './AuthLayout'
import PasswordField from './PasswordField'

/** Resetting a forgotten password: request a code by email, then use it. */
export default function RecoverPage() {
  const { notify } = useToast()
  const navigate = useNavigate()

  const [step, setStep] = useState<'request' | 'reset'>('request')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function requestCode(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const result = await api.post<{ message: string }>('/api/auth/recover/request', { email })
      notify(result.message, 'info')
      setStep('reset')
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not send a code.')
    } finally {
      setBusy(false)
    }
  }

  async function resetPassword(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const result = await api.post<{ message: string }>('/api/auth/recover/reset', {
        email,
        code,
        password,
      })
      notify(result.message, 'success')
      navigate('/signin', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not reset the password.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout eyebrow="Account recovery" headline="Back in, in two steps">
      <h1 className="auth-title">Reset your password</h1>
      <p className="small muted">
        {step === 'request'
          ? 'We will email you a code.'
          : 'Enter the code we emailed you, and choose a new password.'}
      </p>
      <div>
        <form onSubmit={step === 'request' ? requestCode : resetPassword} noValidate>
          {error && <Alert kind="error">{error}</Alert>}

          <Field label="Email" htmlFor="recover-email">
            <input
              id="recover-email"
              type="email"
              value={email}
              autoComplete="email"
              required
              disabled={step === 'reset'}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>

          {step === 'reset' && (
            <>
              <Field label="Verification code" htmlFor="recover-code">
                <input
                  id="recover-code"
                  value={code}
                  inputMode="numeric"
                  required
                  onChange={(e) => setCode(e.target.value)}
                />
              </Field>
              <PasswordField
                id="recover-password"
                label="New password"
                value={password}
                autoComplete="new-password"
                hint="At least eight characters, including a letter and a number."
                onChange={setPassword}
              />
            </>
          )}

          <button className="btn btn-block auth-submit" type="submit" disabled={busy}>
            {step === 'request' ? 'Send code' : 'Reset password'}
          </button>

          <p className="auth-foot small">
            Remembered it? <Link to="/signin">Log in</Link>
          </p>
        </form>
      </div>
    </AuthLayout>
  )
}
