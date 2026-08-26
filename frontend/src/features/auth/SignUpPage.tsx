import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import type { UniversitySummary } from '@/lib/types'
import { Alert, Card, EmptyState, Field, Loading } from '@/components/ui'

/**
 * Creating an account within one university.
 *
 * The university comes from `?university=<slug>`, set by its public page. Without
 * one there is nothing to scope the account or the dropdown values to, so a
 * picker is shown first rather than guessing.
 */
export default function SignUpPage() {
  const [params] = useSearchParams()
  const slug = params.get('university')

  return (
    <div className="auth-card wide">
      {slug ? <SignUpForm slug={slug} /> : <UniversityPicker />}
    </div>
  )
}

function UniversityPicker() {
  const universities = useQuery({
    queryKey: ['public', 'universities'],
    queryFn: () => api.get<UniversitySummary[]>('/api/public/universities'),
  })

  const items = universities.data ?? []

  return (
    <Card title="Which university are you joining?">
      {universities.isLoading ? (
        <Loading rows={3} />
      ) : items.length === 0 ? (
        <EmptyState
          title="No universities are open for registration"
          hint="Once a university is published you will be able to join it here."
        />
      ) : (
        <div className="row">
          {items.map((university) => (
            <Link
              key={university.slug}
              className="btn btn-secondary"
              to={`/signup?university=${encodeURIComponent(university.slug)}`}
            >
              {university.name}
            </Link>
          ))}
        </div>
      )}
    </Card>
  )
}

/**
 * The lists behind the dropdowns, for one university.
 *
 * These come from a public endpoint, so unlike the signed-in equivalent they
 * genuinely load before anybody has an account. There is deliberately no
 * free-text fallback: when the lists failed to load, everyone typed their own
 * spelling of "CSE" and the class groups fragmented one per spelling.
 */
function useMetadata(slug: string) {
  const query = useQuery({
    queryKey: ['public', 'metadata', slug],
    queryFn: async () => {
      const types = ['DEPARTMENT', 'BATCH', 'SEMESTER', 'SECTION', 'DESIGNATION']
      const lists = await Promise.all(
        types.map((type) =>
          api.get<string[]>(
            `/api/public/universities/${slug}/metadata?type=${encodeURIComponent(type)}`,
          ),
        ),
      )
      return Object.fromEntries(types.map((type, index) => [type, lists[index]])) as Record<
        string,
        string[]
      >
    },
    staleTime: Infinity,
  })

  return (type: string) => query.data?.[type] ?? []
}

function SignUpForm({ slug }: { slug: string }) {
  const optionsFor = useMetadata(slug)
  const { notify } = useToast()
  const navigate = useNavigate()

  const [role, setRole] = useState('STUDENT')
  const [form, setForm] = useState({
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
      const result = await api.post<{ message: string }>('/api/auth/signup', {
        ...form,
        role,
        universitySlug: slug,
      })
      notify(result.message, 'success')
      navigate('/signin', { replace: true })
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
    <Card
      title="Create your account"
      actions={
        <Link className="btn btn-secondary btn-sm" to={`/u/${slug}`}>
          About this university
        </Link>
      }
    >
      <form onSubmit={submit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        <Alert kind="info">
          New accounts are reviewed by an administrator before they can be used. You will
          sign in with your email address.
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
            <input
              id="signup-name"
              value={form.fullName}
              required
              onChange={(e) => update('fullName')(e.target.value)}
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
          <Field label="Password" htmlFor="signup-password" error={fieldErrors.password}>
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
            <input
              id="signup-idno"
              value={form.idNo}
              required
              onChange={(e) => update('idNo')(e.target.value)}
            />
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
                <Choice
                  id="signup-batch"
                  value={form.batch}
                  options={optionsFor('BATCH')}
                  onChange={update('batch')}
                />
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
    </Card>
  )
}

/** A dropdown of the university's own options. */
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
  return (
    <select id={id} value={value} onChange={(e) => onChange(e.target.value)} disabled={options.length === 0}>
      <option value="">{options.length === 0 ? 'Loading…' : 'Select…'}</option>
      {options.map((option) => (
        <option key={option} value={option}>
          {option}
        </option>
      ))}
    </select>
  )
}
