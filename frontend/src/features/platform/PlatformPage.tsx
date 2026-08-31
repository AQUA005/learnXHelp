import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { formatDateTime } from '@/lib/format'
import type { PlatformBranding, Role } from '@/lib/types'
import { Alert, Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'
import { VIEW_PARAM } from '@/app/views'
import { PLATFORM_TABS, platformTabFrom } from './tabs'
import type { PlatformTab } from './tabs'
import type { BugReport, ConsoleUniversity } from './types'

/** What each screen calls itself, now that each one is its own destination. */
const HEADINGS: Record<PlatformTab, { title: string; description: string }> = {
  universities: {
    title: 'Universities',
    description: 'Every school on LearnX: add one, list it publicly, or take it down.',
  },
  branding: {
    title: 'Site branding',
    description: 'The name, mark and tagline LearnX itself is presented under.',
  },
  bugs: {
    title: 'Bug reports',
    description: 'Problems reported from any campus, newest first.',
  },
  broadcast: {
    title: 'Broadcast',
    description: 'Email an audience directly, outside any one university.',
  },
}

/**
 * The platform owner's console.
 *
 * Which screen is open lives in the address rather than in local state, so the
 * sidebar links straight to one, a reload stays where it was, and a link to a
 * bug report can be sent to somebody. The tab row is kept as well as the
 * sidebar entries: on a narrow screen the sidebar is behind a menu button.
 */
export default function PlatformPage() {
  const [params, setParams] = useSearchParams()
  const tab = platformTabFrom(params.get(VIEW_PARAM))
  const show = (next: PlatformTab) => setParams({ [VIEW_PARAM]: next }, { replace: true })
  const heading = HEADINGS[tab]

  return (
    <>
      <PageHeader title={heading.title} description={heading.description} />

      <div className="row" style={{ marginBottom: '1rem' }}>
        {PLATFORM_TABS.map((entry) => (
          <button
            key={entry.id}
            className={tab === entry.id ? 'btn btn-sm' : 'btn btn-secondary btn-sm'}
            onClick={() => show(entry.id)}
          >
            {entry.label}
          </button>
        ))}
      </div>

      {tab === 'universities' && <Universities />}
      {tab === 'branding' && <Branding />}
      {tab === 'bugs' && <BugReports />}
      {tab === 'broadcast' && <Broadcast />}
    </>
  )
}

const EMPTY_UNIVERSITY = {
  name: '',
  domain: '',
  description: '',
  contactEmail: '',
  adminFullName: '',
  adminEmail: '',
  adminPassword: '',
}

function Universities() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState(EMPTY_UNIVERSITY)

  const universities = useQuery({
    queryKey: ['platform', 'universities'],
    queryFn: () => api.get<ConsoleUniversity[]>('/api/master/universities'),
  })

  const update = (key: keyof typeof form) => (value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  const create = useMutation({
    mutationFn: () => api.post<ConsoleUniversity>('/api/master/universities', form),
    onSuccess: (created) => {
      notify(`${created.name} created. Add its departments, then list it.`, 'success')
      setForm(EMPTY_UNIVERSITY)
      setAdding(false)
      void queryClient.invalidateQueries({ queryKey: ['platform', 'universities'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  const items = universities.data ?? []
  const listed = items.filter((university) => university.published).length

  return (
    <>
      <Card
        title={
          universities.isLoading
            ? 'Universities'
            : `${items.length} universit${items.length === 1 ? 'y' : 'ies'}, ${listed} listed`
        }
        actions={
          <button className="btn btn-sm" onClick={() => setAdding((open) => !open)}>
            {adding ? 'Cancel' : 'Add a university'}
          </button>
        }
      >
        {universities.isLoading ? (
          <Loading rows={3} />
        ) : items.length === 0 ? (
          <EmptyState
            icon="platform"
            title="No universities yet"
            hint="Add the first one to open LearnX for sign-ups."
          />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>University</th>
                  <th>Public address</th>
                  <th>Administrator</th>
                  <th>People</th>
                  <th>Listing</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((university) => (
                  <tr key={university.id}>
                    <td>
                      <Link className="slot-course" to={`/platform/universities/${university.id}`}>
                        {university.name}
                      </Link>
                      <div className="small muted">{university.domain}</div>
                    </td>
                    <td className="mono small">/u/{university.slug}</td>
                    <td className="small">
                      {university.adminEmail ?? <span className="muted">not set up</span>}
                    </td>
                    <td className="small">{university.userCount}</td>
                    <td>
                      {university.published ? (
                        <Badge kind="success">Listed</Badge>
                      ) : (
                        <Badge kind="warning">Hidden</Badge>
                      )}
                    </td>
                    <td>
                      <Link
                        className="btn btn-secondary btn-sm"
                        to={`/platform/universities/${university.id}`}
                      >
                        Manage
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {adding && (
        <Card title="Add a university">
          <form onSubmit={submit} noValidate>
            <Alert kind="info">
              The university starts hidden. Its administrator signs in with the email address
              below, adds their departments, and then you can list it.
            </Alert>

            <div className="grid grid-2">
              <Field label="Name" htmlFor="uni-name">
                <input
                  id="uni-name"
                  value={form.name}
                  required
                  onChange={(e) => update('name')(e.target.value)}
                />
              </Field>
              <Field label="Domain" htmlFor="uni-domain">
                <input
                  id="uni-domain"
                  value={form.domain}
                  placeholder="example.ac.bd"
                  required
                  onChange={(e) => update('domain')(e.target.value)}
                />
              </Field>
              <Field label="Administrator name" htmlFor="uni-admin-name">
                <input
                  id="uni-admin-name"
                  value={form.adminFullName}
                  onChange={(e) => update('adminFullName')(e.target.value)}
                />
              </Field>
              <Field label="Administrator email" htmlFor="uni-admin-email">
                <input
                  id="uni-admin-email"
                  type="email"
                  value={form.adminEmail}
                  required
                  onChange={(e) => update('adminEmail')(e.target.value)}
                />
              </Field>
              <Field label="Administrator password" htmlFor="uni-admin-password">
                <input
                  id="uni-admin-password"
                  type="password"
                  value={form.adminPassword}
                  autoComplete="new-password"
                  required
                  onChange={(e) => update('adminPassword')(e.target.value)}
                />
              </Field>
              <Field label="Contact email" htmlFor="uni-contact">
                <input
                  id="uni-contact"
                  type="email"
                  value={form.contactEmail}
                  onChange={(e) => update('contactEmail')(e.target.value)}
                />
              </Field>
            </div>

            <Field label="Description" htmlFor="uni-description">
              <textarea
                id="uni-description"
                rows={3}
                value={form.description}
                onChange={(e) => update('description')(e.target.value)}
              />
            </Field>

            <button className="btn" type="submit" disabled={create.isPending}>
              {create.isPending ? 'Creating…' : 'Create university'}
            </button>
          </form>
        </Card>
      )}
    </>
  )
}

function Branding() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const branding = useQuery({
    queryKey: ['platform', 'branding'],
    queryFn: () => api.get<PlatformBranding>('/api/master/branding'),
  })

  const [form, setForm] = useState<{ siteName: string; tagline: string; supportEmail: string } | null>(
    null,
  )
  const current = form ?? {
    siteName: branding.data?.siteName ?? '',
    tagline: branding.data?.tagline ?? '',
    supportEmail: branding.data?.supportEmail ?? '',
  }

  const save = useMutation({
    mutationFn: () => api.put<PlatformBranding>('/api/master/branding', current),
    onSuccess: () => {
      notify('Branding saved', 'success')
      void queryClient.invalidateQueries({ queryKey: ['platform', 'branding'] })
      // The public site reads this too.
      void queryClient.invalidateQueries({ queryKey: ['branding'] })
    },
    onError: (error) => reportError(error),
  })

  const uploadImage = useMutation({
    mutationFn: ({ kind, dataUrl }: { kind: 'logo' | 'icon'; dataUrl: string }) =>
      api.post(`/api/master/branding/${kind}`, { dataUrl }),
    onSuccess: () => {
      notify('Image uploaded', 'success')
      void queryClient.invalidateQueries({ queryKey: ['platform', 'branding'] })
      void queryClient.invalidateQueries({ queryKey: ['branding'] })
    },
    onError: (error) => reportError(error),
  })

  if (branding.isLoading) {
    return (
      <Card title="Site branding">
        <Loading rows={3} />
      </Card>
    )
  }

  return (
    <>
      <Card title="Name and wording">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate()
          }}
          noValidate
        >
          <div className="grid grid-2">
            <Field label="Site name" htmlFor="brand-name">
              <input
                id="brand-name"
                value={current.siteName}
                required
                onChange={(e) => setForm({ ...current, siteName: e.target.value })}
              />
            </Field>
            <Field label="Support email" htmlFor="brand-support">
              <input
                id="brand-support"
                type="email"
                value={current.supportEmail}
                onChange={(e) => setForm({ ...current, supportEmail: e.target.value })}
              />
            </Field>
          </div>

          <Field label="Tagline" htmlFor="brand-tagline">
            <input
              id="brand-tagline"
              value={current.tagline}
              placeholder="Shown as the headline on the public home page"
              onChange={(e) => setForm({ ...current, tagline: e.target.value })}
            />
          </Field>

          <button className="btn" type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
        </form>
      </Card>

      <Card title="Logo and icon">
        <div className="grid grid-2">
          <ImageUpload
            label="Logo"
            hint="Shown in the header and the sidebar."
            currentUrl={branding.data?.logoUrl ?? null}
            busy={uploadImage.isPending}
            onPick={(dataUrl) => uploadImage.mutate({ kind: 'logo', dataUrl })}
          />
          <ImageUpload
            label="Icon"
            hint="The small square mark, used as the browser tab icon."
            currentUrl={branding.data?.iconUrl ?? null}
            busy={uploadImage.isPending}
            onPick={(dataUrl) => uploadImage.mutate({ kind: 'icon', dataUrl })}
          />
        </div>
      </Card>
    </>
  )
}

/**
 * Picks an image and hands it over as a data URL.
 *
 * The server takes data URLs rather than multipart, as avatars already do, and
 * decodes and size-checks them before anything is written.
 */
export function ImageUpload({
  label,
  hint,
  currentUrl,
  busy,
  onPick,
}: {
  label: string
  hint?: string
  currentUrl: string | null
  busy: boolean
  onPick: (dataUrl: string) => void
}) {
  const inputId = `upload-${label.toLowerCase().replace(/\s+/g, '-')}`

  return (
    <div className="field">
      <label htmlFor={inputId}>{label}</label>
      {hint && <p className="small muted">{hint}</p>}
      {currentUrl && <img className="uni-logo uni-logo-lg" src={currentUrl} alt="" />}
      <input
        id={inputId}
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        disabled={busy}
        onChange={(event) => {
          const file = event.target.files?.[0]
          if (!file) return
          const reader = new FileReader()
          reader.onload = () => {
            if (typeof reader.result === 'string') onPick(reader.result)
          }
          reader.readAsDataURL(file)
          // Allows re-picking the same file after a failure.
          event.target.value = ''
        }}
      />
    </div>
  )
}

const BUG_FILTERS = [
  { id: 'OPEN', label: 'Open' },
  { id: 'RESOLVED', label: 'Resolved' },
  { id: 'ALL', label: 'All' },
] as const

type BugFilter = (typeof BUG_FILTERS)[number]['id']

function BugReports() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [filter, setFilter] = useState<BugFilter>('OPEN')

  const bugs = useQuery({
    queryKey: ['platform', 'bugs'],
    queryFn: () => api.get<BugReport[]>('/api/master/bugs'),
  })

  const setStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      api.post(`/api/master/bugs/${id}/status`, { status }),
    onSuccess: () => {
      notify('Status updated', 'success')
      void queryClient.invalidateQueries({ queryKey: ['platform', 'bugs'] })
    },
    onError: (error) => reportError(error),
  })

  const all = bugs.data ?? []
  const items = all.filter((bug) => {
    if (filter === 'ALL') return true
    if (filter === 'RESOLVED') return bug.status === 'RESOLVED'
    return bug.status !== 'RESOLVED'
  })

  return (
    <Card
      title={bugs.isLoading ? 'Bug reports' : `${items.length} of ${all.length} shown`}
      actions={
        <div className="row">
          {BUG_FILTERS.map((entry) => (
            <button
              key={entry.id}
              className={filter === entry.id ? 'btn btn-sm' : 'btn btn-secondary btn-sm'}
              onClick={() => setFilter(entry.id)}
            >
              {entry.label}
            </button>
          ))}
        </div>
      }
    >
      {bugs.isLoading ? (
        <Loading rows={3} />
      ) : items.length === 0 ? (
        <EmptyState
          icon="bug"
          title={filter === 'OPEN' ? 'Nothing outstanding' : 'Nothing to show'}
          hint="Reports filed from the sidebar of any screen land here."
        />
      ) : (
        <div>
          {items.map((bug) => (
            <article className="bug-report" key={bug.id}>
              <header className="bug-report-head">
                <div>
                  <strong>{bug.title}</strong>
                  <div className="small muted">
                    {bug.reportedBy ?? 'Unknown'}
                    {bug.reporterRole ? ` · ${roleNoun(bug.reporterRole)}` : ''}
                    {` · ${bug.universityName ?? 'LearnX'}`}
                    {` · ${formatDateTime(bug.createdAt)}`}
                  </div>
                </div>
                <Badge kind={bug.status === 'RESOLVED' ? 'success' : 'warning'}>{bug.status}</Badge>
              </header>

              <p className="bug-report-body">{bug.description}</p>

              <footer className="bug-report-foot">
                <div className="small muted">
                  {bug.pagePath && <span className="mono">{bug.pagePath}</span>}
                  {bug.reporterEmail && <span> · {bug.reporterEmail}</span>}
                </div>
                <div className="row">
                  {bug.status === 'PENDING' && (
                    <button
                      className="btn btn-secondary btn-sm"
                      disabled={setStatus.isPending}
                      onClick={() => setStatus.mutate({ id: bug.id, status: 'REVIEWED' })}
                    >
                      Reviewed
                    </button>
                  )}
                  {bug.status === 'RESOLVED' ? (
                    <button
                      className="btn btn-secondary btn-sm"
                      disabled={setStatus.isPending}
                      onClick={() => setStatus.mutate({ id: bug.id, status: 'PENDING' })}
                    >
                      Reopen
                    </button>
                  ) : (
                    <button
                      className="btn btn-sm"
                      disabled={setStatus.isPending}
                      onClick={() => setStatus.mutate({ id: bug.id, status: 'RESOLVED' })}
                    >
                      Resolve
                    </button>
                  )}
                </div>
              </footer>
            </article>
          ))}
        </div>
      )}
    </Card>
  )
}

/** The roles a broadcast can single out. */
const AUDIENCE_ROLES: { id: Role; plural: string; singular: string }[] = [
  { id: 'ADMIN', plural: 'Administrators', singular: 'Administrator' },
  { id: 'TEACHER', plural: 'Teachers', singular: 'Teacher' },
  { id: 'CR', plural: 'Class representatives', singular: 'Class representative' },
  { id: 'STUDENT', plural: 'Students', singular: 'Student' },
]

function roleNoun(role: Role): string {
  if (role === 'SYSTEM_ADMIN') return 'Platform owner'
  return AUDIENCE_ROLES.find((entry) => entry.id === role)?.singular ?? role
}

/**
 * Email an audience.
 *
 * The audience is described here and resolved on the server: the addresses are
 * never assembled in the browser, so a send can only reach people who hold
 * accounts. The recipient count is fetched for whatever is selected and shown
 * on the button itself, because "everyone on LearnX" and "the CRs at one
 * university" are otherwise two identical-looking clicks apart.
 */
function Broadcast() {
  const { notify, reportError } = useToast()
  const [subject, setSubject] = useState('')
  const [content, setContent] = useState('')
  const [universityId, setUniversityId] = useState('')
  const [role, setRole] = useState('')
  const [confirmed, setConfirmed] = useState(false)

  const universities = useQuery({
    queryKey: ['platform', 'universities'],
    queryFn: () => api.get<ConsoleUniversity[]>('/api/master/universities'),
  })

  const audience = useQuery({
    queryKey: ['platform', 'audience', universityId, role],
    queryFn: () => {
      const params = new URLSearchParams()
      if (universityId) params.set('universityId', universityId)
      if (role) params.set('role', role)
      const query = params.toString()
      return api.get<{ count: number }>(`/api/master/audience${query ? `?${query}` : ''}`)
    },
  })

  const send = useMutation({
    mutationFn: () =>
      api.post<{ successCount: number; failCount: number }>('/api/master/send-email', {
        subject,
        content,
        universityId: universityId ? Number(universityId) : null,
        role: role || null,
      }),
    onSuccess: (result) => {
      notify(
        result.failCount === 0
          ? `Sent to ${result.successCount} recipients`
          : `Sent to ${result.successCount}, ${result.failCount} could not be reached`,
        result.failCount === 0 ? 'success' : 'info',
      )
      setSubject('')
      setContent('')
      setConfirmed(false)
    },
    onError: (error) => reportError(error),
  })

  const count = audience.data?.count ?? 0
  const chosen = universities.data?.find((university) => String(university.id) === universityId)
  const who = role
    ? (AUDIENCE_ROLES.find((entry) => entry.id === role)?.plural ?? role).toLowerCase()
    : 'everyone'
  const where = chosen ? `at ${chosen.name}` : 'across every university'

  return (
    <Card title="Email an audience">
      <form
        onSubmit={(event) => {
          event.preventDefault()
          send.mutate()
        }}
        noValidate
      >
        <div className="grid grid-2">
          <Field label="University" htmlFor="broadcast-university">
            <select
              id="broadcast-university"
              value={universityId}
              onChange={(e) => {
                setUniversityId(e.target.value)
                setConfirmed(false)
              }}
            >
              <option value="">Every university</option>
              {(universities.data ?? []).map((university) => (
                <option key={university.id} value={university.id}>
                  {university.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Role" htmlFor="broadcast-role">
            <select
              id="broadcast-role"
              value={role}
              onChange={(e) => {
                setRole(e.target.value)
                setConfirmed(false)
              }}
            >
              <option value="">Everyone</option>
              {AUDIENCE_ROLES.map((entry) => (
                <option key={entry.id} value={entry.id}>
                  {entry.plural}
                </option>
              ))}
            </select>
          </Field>
        </div>

        {/* Sending to the whole platform is the one that cannot be taken back
            by apologising to a single administrator, so it is the loud one. */}
        <Alert kind={universityId || role ? 'info' : 'error'}>
          This reaches {audience.isLoading ? '…' : count} {count === 1 ? 'account' : 'accounts'} —{' '}
          {who} {where}. Email cannot be recalled once it has been sent.
        </Alert>

        <Field label="Subject" htmlFor="broadcast-subject">
          <input
            id="broadcast-subject"
            value={subject}
            required
            onChange={(e) => setSubject(e.target.value)}
          />
        </Field>

        <Field label="Message" htmlFor="broadcast-content">
          <textarea
            id="broadcast-content"
            rows={8}
            value={content}
            required
            onChange={(e) => setContent(e.target.value)}
          />
        </Field>

        <div className="option">
          <input
            id="broadcast-confirm"
            type="checkbox"
            checked={confirmed}
            onChange={(e) => setConfirmed(e.target.checked)}
          />
          <label htmlFor="broadcast-confirm">
            I understand this emails {count} {count === 1 ? 'person' : 'people'}.
          </label>
        </div>

        <button
          className="btn"
          type="submit"
          disabled={send.isPending || !confirmed || !subject || !content || count === 0}
        >
          {send.isPending ? 'Sending…' : `Send to ${count}`}
        </button>
      </form>
    </Card>
  )
}
