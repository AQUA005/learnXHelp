import { useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import type { UniversitySummary } from '@/lib/types'
import { Alert, EmptyState, Field, Loading } from '@/components/ui'
import AuthLayout from './AuthLayout'
import PasswordField from './PasswordField'

/**
 * Creating an account, one question at a time.
 *
 * The old form put eleven fields on one page and asked people to scroll
 * through them. Signing up is not a form to be filled so much as a short
 * conversation -- who you are, where you study, and a password -- so it is
 * asked that way. Each step holds only what belongs together, nothing is
 * submitted until the last one, and going back changes nothing.
 *
 * The university may arrive in `?university=<slug>` from its public page, in
 * which case that question is simply not asked.
 */
export default function SignUpPage() {
  const [params] = useSearchParams()
  const [chosen, setChosen] = useState<string | null>(null)
  const slug = params.get('university') ?? chosen

  // Somebody arriving from a university page has already answered the first
  // question, so it is neither asked again nor counted against them.
  const preamble = params.get('university') ? 0 : 1

  return slug ? (
    <SignUpSteps slug={slug} preamble={preamble} />
  ) : (
    <UniversityStep onChoose={setChosen} />
  )
}

/** The first question, when nobody has answered it for us. */
function UniversityStep({ onChoose }: { onChoose: (slug: string) => void }) {
  const universities = useQuery({
    queryKey: ['public', 'universities'],
    queryFn: () => api.get<UniversitySummary[]>('/api/public/universities'),
  })

  const items = universities.data ?? []

  return (
    <AuthLayout eyebrow="Create your account" headline="Which university are you joining?">
      {/* Six: this one, then role, name, department, class and password. A
          teacher's path is one shorter, which the count reflects once they say
          so. */}
      <StepHeading step={1} of={6} title="Your university" hint="Pick where you study or teach." />

      {universities.isLoading ? (
        <Loading rows={3} />
      ) : items.length === 0 ? (
        <EmptyState
          icon="platform"
          title="No universities are open for registration"
          hint="Once a university is published you will be able to join it here."
        />
      ) : (
        <div className="choice-list">
          {items.map((university) => (
            <button
              key={university.slug}
              type="button"
              className="choice"
              onClick={() => onChoose(university.slug)}
            >
              <span className="choice-title">{university.name}</span>
            </button>
          ))}
        </div>
      )}

      <p className="auth-foot small">
        Already have an account? <Link to="/signin">Log in</Link>
      </p>
    </AuthLayout>
  )
}

type Form = {
  role: string
  fullName: string
  email: string
  password: string
  idNo: string
  department: string
  batch: string
  semester: string
  section: string
  designation: string
}

const EMPTY: Form = {
  role: 'STUDENT',
  fullName: '',
  email: '',
  password: '',
  idNo: '',
  department: '',
  batch: '',
  semester: '',
  section: '',
  designation: '',
}

const ROLES = [
  { id: 'STUDENT', title: 'Student', hint: 'Your routine, notes, exams and results.' },
  { id: 'CR', title: 'Class representative', hint: 'A student who also keeps the class up to date.' },
  { id: 'TEACHER', title: 'Teacher', hint: 'Your classes, marking and note approvals.' },
]

function SignUpSteps({ slug, preamble }: { slug: string; preamble: number }) {
  const optionsFor = useMetadata(slug)
  const { notify } = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState<Form>(EMPTY)
  const [index, setIndex] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  const set = (key: keyof Form) => (value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  const isStudentLike = form.role === 'STUDENT' || form.role === 'CR'

  /**
   * The questions, in order.
   *
   * `ready` is what the Continue button obeys, so a step cannot be left half
   * answered and the server is never asked to reject something the page
   * already knew about. `fields` lets a rejected submission come back to the
   * step that owns the offending value.
   */
  const steps: Step[] = useMemo(
    () => [
      {
        title: 'Who are you joining as?',
        hint: 'This decides what LearnX shows you.',
        fields: ['role'],
        ready: Boolean(form.role),
        content: (
          <div className="choice-list">
            {ROLES.map((role) => (
              <button
                key={role.id}
                type="button"
                className={form.role === role.id ? 'choice selected' : 'choice'}
                aria-pressed={form.role === role.id}
                onClick={() => set('role')(role.id)}
              >
                <span className="choice-title">{role.title}</span>
                <span className="choice-hint">{role.hint}</span>
              </button>
            ))}
          </div>
        ),
      },
      {
        title: 'What should we call you?',
        hint: 'Your email address is what you will sign in with.',
        fields: ['fullName', 'email'],
        ready: form.fullName.trim().length > 1 && /.+@.+\..+/.test(form.email),
        content: (
          <>
            <Field label="Full name" htmlFor="signup-name" error={fieldErrors.fullName}>
              <input
                id="signup-name"
                value={form.fullName}
                autoComplete="name"
                required
                onChange={(event) => set('fullName')(event.target.value)}
              />
            </Field>
            <Field label="Email" htmlFor="signup-email" error={fieldErrors.email}>
              <input
                id="signup-email"
                type="email"
                value={form.email}
                autoComplete="email"
                placeholder="you@university.edu"
                required
                onChange={(event) => set('email')(event.target.value)}
              />
            </Field>
          </>
        ),
      },
      {
        title: isStudentLike ? 'Where do you study?' : 'Where do you teach?',
        hint: 'Your department decides whose routine and notes you see.',
        fields: ['department', 'idNo', 'designation'],
        ready: Boolean(form.department) && form.idNo.trim().length > 0,
        content: (
          <>
            <Field label="Department" htmlFor="signup-department" error={fieldErrors.department}>
              <Choice
                id="signup-department"
                value={form.department}
                options={optionsFor('DEPARTMENT')}
                onChange={set('department')}
              />
            </Field>
            <Field label="ID number" htmlFor="signup-idno" error={fieldErrors.idNo}>
              <input
                id="signup-idno"
                value={form.idNo}
                required
                onChange={(event) => set('idNo')(event.target.value)}
              />
            </Field>
            {!isStudentLike && (
              <Field label="Designation" htmlFor="signup-designation">
                <Choice
                  id="signup-designation"
                  value={form.designation}
                  options={optionsFor('DESIGNATION')}
                  onChange={set('designation')}
                />
              </Field>
            )}
          </>
        ),
      },
      // Only a student has a class; a teacher's third question was their last.
      ...(isStudentLike
        ? [
            {
              title: 'Which class are you in?',
              hint: 'This is the routine and the notes you will be shown.',
              fields: ['batch', 'semester', 'section'],
              ready: Boolean(form.batch && form.semester && form.section),
              content: (
                <>
                  <Field label="Batch" htmlFor="signup-batch">
                    <Choice
                      id="signup-batch"
                      value={form.batch}
                      options={optionsFor('BATCH')}
                      onChange={set('batch')}
                    />
                  </Field>
                  <Field label="Semester" htmlFor="signup-semester">
                    <Choice
                      id="signup-semester"
                      value={form.semester}
                      options={optionsFor('SEMESTER')}
                      onChange={set('semester')}
                    />
                  </Field>
                  <Field label="Section" htmlFor="signup-section">
                    <Choice
                      id="signup-section"
                      value={form.section}
                      options={optionsFor('SECTION')}
                      onChange={set('section')}
                    />
                  </Field>
                </>
              ),
            } satisfies Step,
          ]
        : []),
      {
        title: 'Choose a password',
        hint: 'An administrator reviews new accounts before they can be used.',
        fields: ['password'],
        ready: form.password.length >= 8,
        content: (
          <PasswordField
            id="signup-password"
            label="Password"
            value={form.password}
            autoComplete="new-password"
            error={fieldErrors.password}
            hint="At least eight characters, including a letter and a number."
            onChange={set('password')}
          />
        ),
      },
    ],
    [form, fieldErrors, isStudentLike, optionsFor],
  )

  const step = steps[Math.min(index, steps.length - 1)]
  const last = index === steps.length - 1

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!step.ready) return
    if (!last) {
      setIndex((current) => current + 1)
      return
    }

    setError(null)
    setFieldErrors({})
    setBusy(true)
    try {
      const result = await api.post<{ message: string }>('/api/auth/signup', {
        ...form,
        universitySlug: slug,
      })
      notify(result.message, 'success')
      navigate('/signin', { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
        setFieldErrors(caught.fieldErrors)
        // Back to the question the server took issue with, rather than leaving
        // the reader on the password step wondering which answer was wrong.
        const offending = Object.keys(caught.fieldErrors)[0]
        const owner = steps.findIndex((entry) => entry.fields.includes(offending))
        if (owner >= 0) setIndex(owner)
      } else {
        setError('Could not create the account. Please try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout eyebrow="Create your account" headline={step.title}>
      <StepHeading
        step={index + 1 + preamble}
        of={steps.length + preamble}
        title={step.title}
        hint={step.hint}
      />

      <form onSubmit={submit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}

        <div className="step-body" key={index}>
          {step.content}
        </div>

        <div className="step-actions">
          {index > 0 && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setIndex((current) => current - 1)}
            >
              Back
            </button>
          )}
          <button className="btn auth-submit" type="submit" disabled={!step.ready || busy}>
            {busy ? 'Creating…' : last ? 'Create account' : 'Continue'}
          </button>
        </div>

        <p className="auth-foot small">
          Already have an account? <Link to="/signin">Log in</Link>
        </p>
      </form>
    </AuthLayout>
  )
}

type Step = {
  title: string
  hint: string
  /** Which values this step owns, so a server error can point back at it. */
  fields: string[]
  ready: boolean
  content: ReactNode
}

/** Where you are, and what is being asked. */
function StepHeading({
  step,
  of,
  title,
  hint,
}: {
  step: number
  of: number
  title: string
  hint: string
}) {
  return (
    <header className="step-head">
      <div className="step-progress" role="presentation">
        {Array.from({ length: of }, (_, i) => (
          <span key={i} className={i < step ? 'step-dot done' : 'step-dot'} />
        ))}
      </div>
      <p className="step-count small">
        Step {step} of {of}
      </p>
      <h1 className="auth-title">{title}</h1>
      <p className="small muted">{hint}</p>
    </header>
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
    <select
      id={id}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      disabled={options.length === 0}
    >
      <option value="">{options.length === 0 ? 'Loading…' : 'Select…'}</option>
      {options.map((option) => (
        <option key={option} value={option}>
          {option}
        </option>
      ))}
    </select>
  )
}
