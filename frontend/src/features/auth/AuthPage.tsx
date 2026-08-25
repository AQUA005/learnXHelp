import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '@/lib/api'
import { useSession } from '@/lib/session'
import { useToast } from '@/lib/toast'
import type { MetadataOption } from '@/lib/types'
import { Alert, Card, Field } from '@/components/ui'

type Mode = 'signIn' | 'signUp' | 'recover'

export default function AuthPage() {
  const [mode, setMode] = useState<Mode>('signIn')

  return (
    <div className="auth-shell">
      <div className={mode === 'signUp' ? 'auth-card wide' : 'auth-card'}>
        <div className="auth-brand">
          <h1>LearnX</h1>
          <p className="muted small">
            Class routines, study notes, announcements and exams, in one place.
          </p>
        </div>

        <Card>
          <div className="auth-tabs">
            <button
              type="button"
              className={mode === 'signIn' ? 'btn' : 'btn btn-secondary'}
              onClick={() => setMode('signIn')}
            >
              Sign in
            </button>
            <button
              type="button"
              className={mode === 'signUp' ? 'btn' : 'btn btn-secondary'}
              onClick={() => setMode('signUp')}
            >
              Create account
            </button>
          </div>

          {mode === 'signIn' && <SignInForm onRecover={() => setMode('recover')} />}
          {mode === 'signUp' && <SignUpForm onDone={() => setMode('signIn')} />}
          {mode === 'recover' && <RecoverForm onDone={() => setMode('signIn')} />}
        </Card>
      </div>
    </div>
  )
}

function SignInForm({ onRecover }: { onRecover: () => void }) {
  const { signIn } = useSession()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await signIn(username, password)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not sign in. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      {error && <Alert kind="error">{error}</Alert>}

      <Field label="Username" htmlFor="signin-username">
        <input
          id="signin-username"
          value={username}
          autoComplete="username"
          required
          onChange={(e) => setUsername(e.target.value)}
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
        <button type="button" className="btn btn-secondary btn-sm" onClick={onRecover}>
          Forgotten your password?
        </button>
      </div>
    </form>
  )
}

/** Options for the dropdowns, grouped by the metadata type they came from. */
function useMetadata() {
  const [options, setOptions] = useState<MetadataOption[]>([])

  useEffect(() => {
    let active = true
    void api
      .get<MetadataOption[]>('/api/metadata')
      .then((data) => {
        if (active) setOptions(data)
      })
      // Signup still works if the lists cannot be loaded; the fields are typed.
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [])

  return (type: string) => options.filter((option) => option.type === type).map((o) => o.value)
}

function SignUpForm({ onDone }: { onDone: () => void }) {
  const optionsFor = useMetadata()
  const { notify } = useToast()
  const [role, setRole] = useState('STUDENT')
  const [form, setForm] = useState({
    username: '',
    password: '',
    fullName: '',
    email: '',
    idNo: '',
    department: '',
    batch: '',
    semester: '',
    section: '',
    designation: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  const update = (key: keyof typeof form) => (value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  const isStudentLike = role === 'STUDENT' || role === 'CR'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setBusy(true)
    try {
      const result = await api.post<{ message: string }>('/api/auth/signup', { ...form, role })
      notify(result.message, 'success')
      onDone()
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
        setFieldErrors(caught.fieldErrors)
      } else {
        setError('Could not create the account. Please try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      {error && <Alert kind="error">{error}</Alert>}
      <Alert kind="info">
        New accounts are reviewed by an administrator before they can be used.
      </Alert>

      <Field label="I am a" htmlFor="signup-role">
        <select id="signup-role" value={role} onChange={(e) => setRole(e.target.value)}>
          <option value="STUDENT">Student</option>
          <option value="CR">Class representative</option>
          <option value="TEACHER">Teacher</option>
        </select>
      </Field>

      <div className="grid grid-2">
        <Field label="Full name" htmlFor="signup-name" error={fieldErrors.fullName}>
          <input id="signup-name" value={form.fullName} required onChange={(e) => update('fullName')(e.target.value)} />
        </Field>
        <Field label="Username" htmlFor="signup-username" error={fieldErrors.username}>
          <input
            id="signup-username"
            value={form.username}
            autoComplete="username"
            required
            onChange={(e) => update('username')(e.target.value)}
          />
        </Field>
        <Field label="Email" htmlFor="signup-email" error={fieldErrors.email}>
          <input
            id="signup-email"
            type="email"
            value={form.email}
            autoComplete="email"
            required
            onChange={(e) => update('email')(e.target.value)}
          />
        </Field>
        <Field
          label="Password"
          htmlFor="signup-password"
          error={fieldErrors.password}
        >
          <input
            id="signup-password"
            type="password"
            value={form.password}
            autoComplete="new-password"
            required
            onChange={(e) => update('password')(e.target.value)}
          />
        </Field>
        <Field label="ID number" htmlFor="signup-idno" error={fieldErrors.idNo}>
          <input id="signup-idno" value={form.idNo} required onChange={(e) => update('idNo')(e.target.value)} />
        </Field>
        <Field label="Department" htmlFor="signup-department" error={fieldErrors.department}>
          <Choice
            id="signup-department"
            value={form.department}
            options={optionsFor('DEPARTMENT')}
            onChange={update('department')}
          />
        </Field>

        {isStudentLike && (
          <>
            <Field label="Batch" htmlFor="signup-batch">
              <Choice id="signup-batch" value={form.batch} options={optionsFor('BATCH')} onChange={update('batch')} />
            </Field>
            <Field label="Semester" htmlFor="signup-semester">
              <Choice
                id="signup-semester"
                value={form.semester}
                options={optionsFor('SEMESTER')}
                onChange={update('semester')}
              />
            </Field>
            <Field label="Section" htmlFor="signup-section">
              <Choice
                id="signup-section"
                value={form.section}
                options={optionsFor('SECTION')}
                onChange={update('section')}
              />
            </Field>
          </>
        )}

        {role === 'TEACHER' && (
          <Field label="Designation" htmlFor="signup-designation">
            <Choice
              id="signup-designation"
              value={form.designation}
              options={optionsFor('DESIGNATION')}
              onChange={update('designation')}
            />
          </Field>
        )}
      </div>

      <p className="small muted">
        Passwords need at least eight characters, including a letter and a number.
      </p>

      <button className="btn btn-block" type="submit" disabled={busy}>
        {busy ? 'Submitting…' : 'Create account'}
      </button>
    </form>
  )
}

/** A dropdown when the list is known, a free-text box when it is not. */
function Choice({
  id,
  value,
  options,
  onChange,
}: {
  id: string
  value: string
  options: string[]
  onChange: (value: string) => void
}) {
  if (options.length === 0) {
    return <input id={id} value={value} onChange={(e) => onChange(e.target.value)} />
  }
  return (
    <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">Select…</option>
      {options.map((option) => (
        <option key={option} value={option}>
          {option}
        </option>
      ))}
    </select>
  )
}

function RecoverForm({ onDone }: { onDone: () => void }) {
  const { notify } = useToast()
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
      onDone()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not reset the password.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={step === 'request' ? requestCode : resetPassword} noValidate>
      {error && <Alert kind="error">{error}</Alert>}

      <Field label="Email or username" htmlFor="recover-email">
        <input
          id="recover-email"
          value={email}
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
        <button type="button" className="btn btn-secondary btn-sm" onClick={onDone}>
          Back to sign in
        </button>
      </div>
    </form>
  )
}
