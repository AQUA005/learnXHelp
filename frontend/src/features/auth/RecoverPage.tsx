import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { Alert, Card, Field } from '@/components/ui'

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
    <div className="auth-card">
      <Card title="Reset your password">
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
              <Field label="New password" htmlFor="recover-password">
                <input
                  id="recover-password"
                  type="password"
                  value={password}
                  autoComplete="new-password"
                  required
                  onChange={(e) => setPassword(e.target.value)}
                />
              </Field>
            </>
          )}

          <button className="btn btn-block" type="submit" disabled={busy}>
            {step === 'request' ? 'Send code' : 'Reset password'}
          </button>

          <div className="row row-end" style={{ marginTop: '0.7rem' }}>
            <Link className="btn btn-secondary btn-sm" to="/signin">
              Back to sign in
            </Link>
          </div>
        </form>
      </Card>
    </div>
  )
}
