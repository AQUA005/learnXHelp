import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '@/lib/api'
import { useSession } from '@/lib/session'
import { useBranding } from '@/lib/branding'
import { Alert, Field } from '@/components/ui'
import AuthLayout from './AuthLayout'
import PasswordField from './PasswordField'

/**
 * Signing in with an email address.
 *
 * `?next=` carries the page the visitor was trying to reach, so a shared deep
 * link survives the detour through here. Following a link to `/exams/12` used
 * to land on the dashboard with the destination silently lost.
 */
export default function SignInPage() {
  const { signIn } = useSession()
  const branding = useBranding()
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
    <AuthLayout
      eyebrow="Welcome back"
      headline={branding.tagline ?? 'Your campus, in one place'}
    >
      <h1 className="auth-title">Log in</h1>

      <form onSubmit={submit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}

        <Field label="Email" htmlFor="signin-email">
          <input
            id="signin-email"
            type="email"
            value={email}
            autoComplete="email"
            placeholder="you@university.edu"
            required
            onChange={(event) => setEmail(event.target.value)}
          />
        </Field>

        <PasswordField
          id="signin-password"
          label="Password"
          value={password}
          autoComplete="current-password"
          onChange={setPassword}
        />

        <div className="auth-row">
          <Link className="auth-quiet-link" to="/recover">
            Forgotten your password?
          </Link>
        </div>

        <button className="btn btn-block auth-submit" type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Log in'}
        </button>

        <p className="auth-foot small">
          New here? <Link to="/signup">Create an account</Link>
        </p>
      </form>
    </AuthLayout>
  )
}
