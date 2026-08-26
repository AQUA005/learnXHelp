import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '@/lib/api'
import { useSession } from '@/lib/session'
import { Alert, Card, Field } from '@/components/ui'

/**
 * Signing in with an email address.
 *
 * `?next=` carries the page the visitor was trying to reach, so a shared deep
 * link survives the detour through here. Following a link to `/exams/12` used
 * to land on the dashboard with the destination silently lost.
 */
export default function SignInPage() {
  const { signIn } = useSession()
  const navigate = useNavigate()
  const [params] = useSearchParams()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await signIn(email, password)
      navigate(params.get('next') ?? '/', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not sign in. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-card">
      <Card title="Sign in">
        <form onSubmit={submit} noValidate>
          {error && <Alert kind="error">{error}</Alert>}

          <Field label="Email" htmlFor="signin-email">
            <input
              id="signin-email"
              type="email"
              value={email}
              autoComplete="email"
              required
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>

          <Field label="Password" htmlFor="signin-password">
            <input
              id="signin-password"
              type="password"
              value={password}
              autoComplete="current-password"
              required
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>

          <button className="btn btn-block" type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>

          <div className="row row-end" style={{ marginTop: '0.7rem' }}>
            <Link className="btn btn-secondary btn-sm" to="/recover">
              Forgotten your password?
            </Link>
            <Link className="btn btn-secondary btn-sm" to="/signup">
              Create account
            </Link>
          </div>
        </form>
      </Card>
    </div>
  )
}
