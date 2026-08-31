import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import type { Role } from '@/lib/types'
import { Alert, Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'
import { ImageUpload } from './PlatformPage'
import type { ConsoleUniversity, TenantUser, TenantUsers } from './types'

/** One university: who is on it, how it is listed, and how to take it down. */
export default function UniversityDetailPage() {
  const { id = '' } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const university = useQuery({
    queryKey: ['platform', 'university', id],
    queryFn: () => api.get<ConsoleUniversity>(`/api/master/universities/${id}`),
  })

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: ['platform', 'university', id] })
    void queryClient.invalidateQueries({ queryKey: ['platform', 'universities'] })
    // The public homepage lists the same universities.
    void queryClient.invalidateQueries({ queryKey: ['public'] })
  }

  const setPublished = useMutation({
    mutationFn: (published: boolean) =>
      api.put<ConsoleUniversity>(`/api/master/universities/${id}/publish`, { published }),
    onSuccess: (saved) => {
      notify(saved.published ? 'Listed publicly' : 'Hidden from the home page', 'success')
      invalidate()
    },
    onError: (error) => reportError(error),
  })

  const uploadLogo = useMutation({
    mutationFn: (dataUrl: string) => api.post(`/api/master/universities/${id}/logo`, { dataUrl }),
    onSuccess: () => {
      notify('Logo uploaded', 'success')
      invalidate()
    },
    onError: (error) => reportError(error),
  })

  if (university.isLoading) {
    return <Loading rows={5} label="Loading the university" />
  }
  if (!university.data) {
    return <Alert kind="error">Could not load that university.</Alert>
  }

  const current = university.data

  return (
    <>
      <PageHeader title={current.name} description={`Public address: /u/${current.slug}`} />

      <div className="row" style={{ marginBottom: '1rem' }}>
        <Link className="btn btn-secondary btn-sm" to="/platform">
          All universities
        </Link>
        {current.published ? <Badge kind="success">Listed</Badge> : <Badge kind="warning">Hidden</Badge>}
        <span className="small muted">
          {current.userCount} {current.userCount === 1 ? 'account' : 'accounts'}
        </span>
      </div>

      <People id={id} />

      <Card title="Listing">
        <p className="small muted">
          A listed university appears on the public home page and is open for sign-ups. Hiding
          one closes new sign-ups and removes it from the home page — it does not sign anybody
          out, and everybody already there keeps working as before.
        </p>
        <button
          className={current.published ? 'btn btn-secondary' : 'btn'}
          disabled={setPublished.isPending}
          onClick={() => setPublished.mutate(!current.published)}
        >
          {current.published ? 'Hide from the home page' : 'List publicly'}
        </button>
      </Card>

      <ProfileForm university={current} onSaved={invalidate} />

      <Card title="Logo">
        <ImageUpload
          label="University logo"
          hint="Shown on the home page and on this university's public page."
          currentUrl={current.logoUrl}
          busy={uploadLogo.isPending}
          onPick={(dataUrl) => uploadLogo.mutate(dataUrl)}
        />
      </Card>

      <ResetAdmin id={id} />
      <DangerZone university={current} />
    </>
  )
}

/** The role tabs, in the order a campus is usually read: staff first. */
const ROLE_TABS: { id: Role | 'ALL'; label: string }[] = [
  { id: 'ALL', label: 'Everyone' },
  { id: 'ADMIN', label: 'Administrators' },
  { id: 'TEACHER', label: 'Teachers' },
  { id: 'CR', label: 'Class representatives' },
  { id: 'STUDENT', label: 'Students' },
]

/**
 * Who is on this campus.
 *
 * Read-only on purpose. The platform owner needs to see that a university is
 * actually being used, and by whom, but approving and placing accounts belongs
 * to that university's own administrator — they are the one who knows whether
 * a name belongs there. There is deliberately no approve, reject or promote
 * button here.
 */
function People({ id }: { id: string }) {
  const [role, setRole] = useState<Role | 'ALL'>('ALL')
  const [search, setSearch] = useState('')

  const people = useQuery({
    queryKey: ['platform', 'university', id, 'users'],
    queryFn: () => api.get<TenantUsers>(`/api/master/universities/${id}/users`),
  })

  const data = people.data
  const term = search.trim().toLowerCase()
  const shown = (data?.users ?? []).filter((person) => {
    if (role !== 'ALL' && person.role !== role) return false
    if (!term) return true
    return (
      person.fullName?.toLowerCase().includes(term) ||
      person.email?.toLowerCase().includes(term) ||
      person.department?.toLowerCase().includes(term)
    )
  })

  const countFor = (tab: Role | 'ALL') =>
    tab === 'ALL' ? (data?.total ?? 0) : (data?.byRole?.[tab] ?? 0)

  return (
    <Card
      title={people.isLoading ? 'People' : `${data?.total ?? 0} people`}
      actions={
        <input
          type="search"
          value={search}
          placeholder="Search name or email"
          aria-label="Search people"
          onChange={(event) => setSearch(event.target.value)}
        />
      }
    >
      <div className="row" style={{ marginBottom: '0.9rem' }}>
        {ROLE_TABS.map((tab) => (
          <button
            key={tab.id}
            className={role === tab.id ? 'btn btn-sm' : 'btn btn-secondary btn-sm'}
            onClick={() => setRole(tab.id)}
          >
            {tab.label}
            <span className="count-pill">{people.isLoading ? '–' : countFor(tab.id)}</span>
          </button>
        ))}
      </div>

      {people.isLoading ? (
        <Loading rows={4} />
      ) : shown.length === 0 ? (
        <EmptyState
          icon="people"
          title={term ? 'Nobody matches that search' : 'Nobody here yet'}
          hint={
            term
              ? 'Try part of a name or an email address.'
              : 'People appear once they sign up and their administrator approves them.'
          }
        />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Department</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((person) => (
                <PersonRow key={person.id} person={person} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

function PersonRow({ person }: { person: TenantUser }) {
  const where = [person.batch, person.section].filter(Boolean).join(' · ')
  return (
    <tr>
      <td>{person.fullName}</td>
      <td className="small">{person.email}</td>
      <td className="small">{roleLabel(person.role)}</td>
      <td className="small">
        {person.department ?? <span className="muted">—</span>}
        {where && <div className="small muted">{where}</div>}
      </td>
      <td>
        {person.approved ? (
          <Badge kind="success">Approved</Badge>
        ) : (
          <Badge kind="warning">Awaiting approval</Badge>
        )}
      </td>
    </tr>
  )
}

function roleLabel(role: Role): string {
  switch (role) {
    case 'STUDENT':
      return 'Student'
    case 'CR':
      return 'Class representative'
    case 'TEACHER':
      return 'Teacher'
    case 'ADMIN':
      return 'Administrator'
    case 'SYSTEM_ADMIN':
      return 'Platform owner'
  }
}

function ProfileForm({
  university,
  onSaved,
}: {
  university: ConsoleUniversity
  onSaved: () => void
}) {
  const { notify, reportError } = useToast()
  const [form, setForm] = useState({
    name: university.name,
    domain: university.domain,
    description: university.description ?? '',
    contactEmail: university.contactEmail ?? '',
    contactPhone: university.contactPhone ?? '',
    website: university.website ?? '',
    address: university.address ?? '',
  })

  const update = (key: keyof typeof form) => (value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  const save = useMutation({
    mutationFn: () => api.put(`/api/master/universities/${university.id}`, form),
    onSuccess: () => {
      notify('Saved', 'success')
      onSaved()
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  return (
    <Card title="Public profile">
      <form onSubmit={submit} noValidate>
        <div className="grid grid-2">
          <Field label="Name" htmlFor="detail-name">
            <input
              id="detail-name"
              value={form.name}
              required
              onChange={(e) => update('name')(e.target.value)}
            />
          </Field>
          <Field label="Domain" htmlFor="detail-domain">
            <input
              id="detail-domain"
              value={form.domain}
              required
              onChange={(e) => update('domain')(e.target.value)}
            />
          </Field>
          <Field label="Contact email" htmlFor="detail-contact-email">
            <input
              id="detail-contact-email"
              type="email"
              value={form.contactEmail}
              onChange={(e) => update('contactEmail')(e.target.value)}
            />
          </Field>
          <Field label="Contact phone" htmlFor="detail-contact-phone">
            <input
              id="detail-contact-phone"
              value={form.contactPhone}
              onChange={(e) => update('contactPhone')(e.target.value)}
            />
          </Field>
          <Field label="Website" htmlFor="detail-website">
            <input
              id="detail-website"
              value={form.website}
              onChange={(e) => update('website')(e.target.value)}
            />
          </Field>
          <Field label="Address" htmlFor="detail-address">
            <input
              id="detail-address"
              value={form.address}
              onChange={(e) => update('address')(e.target.value)}
            />
          </Field>
        </div>

        <Field label="Description" htmlFor="detail-description">
          <textarea
            id="detail-description"
            rows={4}
            value={form.description}
            onChange={(e) => update('description')(e.target.value)}
          />
        </Field>

        <p className="small muted">
          The public address <span className="mono">/u/{university.slug}</span> is fixed, so
          links people have shared keep working even if the name changes.
        </p>

        <button className="btn" type="submit" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save profile'}
        </button>
      </form>
    </Card>
  )
}

function ResetAdmin({ id }: { id: string }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const reset = useMutation({
    mutationFn: () =>
      api.post<{ message: string }>(`/api/master/universities/${id}/reset-admin`, {
        adminEmail: email,
        adminPassword: password,
      }),
    onSuccess: (result) => {
      notify(result.message, 'success')
      setPassword('')
      // An address with no account creates one, which changes the roll.
      void queryClient.invalidateQueries({ queryKey: ['platform', 'university', id] })
    },
    onError: (error) => reportError(error),
  })

  return (
    <Card title="Administrator access">
      <form
        onSubmit={(event) => {
          event.preventDefault()
          reset.mutate()
        }}
        noValidate
      >
        <p className="small muted">
          Sets a new password for this university's administrator, for when they have locked
          themselves out. If no account has that address, one is created and becomes this
          university's administrator.
        </p>
        <div className="grid grid-2">
          <Field label="Administrator email" htmlFor="reset-email">
            <input
              id="reset-email"
              type="email"
              value={email}
              required
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          <Field label="New password" htmlFor="reset-password">
            <input
              id="reset-password"
              type="password"
              value={password}
              autoComplete="new-password"
              required
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>
        </div>
        <button className="btn btn-secondary" type="submit" disabled={reset.isPending}>
          {reset.isPending ? 'Updating…' : 'Set password'}
        </button>
      </form>
    </Card>
  )
}

/**
 * Deleting a university.
 *
 * Behind a type-the-name confirmation because it removes every account, exam,
 * note, routine and result belonging to it, and there is no undo. Hiding it is
 * offered alongside, since that is what is usually wanted.
 */
function DangerZone({ university }: { university: ConsoleUniversity }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [typed, setTyped] = useState('')

  const remove = useMutation({
    mutationFn: () => api.del<{ message: string }>(`/api/master/universities/${university.id}`),
    onSuccess: () => {
      notify(`${university.name} deleted`, 'success')
      void queryClient.invalidateQueries({ queryKey: ['platform'] })
      void queryClient.invalidateQueries({ queryKey: ['public'] })
      navigate('/platform', { replace: true })
    },
    onError: (error) => reportError(error),
  })

  return (
    <Card title="Delete this university">
      <Alert kind="error">
        This permanently removes {university.userCount}{' '}
        {university.userCount === 1 ? 'account' : 'accounts'} and every class, routine, note,
        exam and result belonging to {university.name}. It cannot be undone. To stop new
        sign-ups without losing anything, hide it instead.
      </Alert>

      <Field label={`Type "${university.name}" to confirm`} htmlFor="delete-confirm">
        <input id="delete-confirm" value={typed} onChange={(e) => setTyped(e.target.value)} />
      </Field>

      <button
        className="btn btn-danger"
        disabled={typed !== university.name || remove.isPending}
        onClick={() => remove.mutate()}
      >
        {remove.isPending ? 'Deleting…' : 'Delete permanently'}
      </button>
    </Card>
  )
}
