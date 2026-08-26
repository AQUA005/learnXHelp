import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import type { PlatformBranding } from '@/lib/types'
import { Alert, Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'
import type { ConsoleUniversity } from './types'

type Tab = 'universities' | 'branding' | 'bugs' | 'broadcast'

/** The platform owner's console: the universities on LearnX, and LearnX itself. */
export default function PlatformPage() {
  const [tab, setTab] = useState<Tab>('universities')

  const tabs: { id: Tab; label: string }[] = [
    { id: 'universities', label: 'Universities' },
    { id: 'branding', label: 'Site branding' },
    { id: 'bugs', label: 'Bug reports' },
    { id: 'broadcast', label: 'Broadcast' },
  ]

  return (
    <>
      <PageHeader
        title="Platform"
        description="The universities using LearnX, and how LearnX itself is presented."
      />

      <div className="row" style={{ marginBottom: '1rem' }}>
        {tabs.map((entry) => (
          <button
            key={entry.id}
            className={tab === entry.id ? 'btn btn-sm' : 'btn btn-secondary btn-sm'}
            onClick={() => setTab(entry.id)}
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

function Universities() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const universities = useQuery({
    queryKey: ['platform', 'universities'],
    queryFn: () => api.get<ConsoleUniversity[]>('/api/master/universities'),
  })

  const [form, setForm] = useState({
    name: '',
    domain: '',
    description: '',
    contactEmail: '',
    adminFullName: '',
    adminEmail: '',
    adminPassword: '',
  })

  const update = (key: keyof typeof form) => (value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  const create = useMutation({
    mutationFn: () => api.post<ConsoleUniversity>('/api/master/universities', form),
    onSuccess: (created) => {
      notify(`${created.name} created. Add its departments, then publish it.`, 'success')
      setForm({
        name: '',
        domain: '',
        description: '',
        contactEmail: '',
        adminFullName: '',
        adminEmail: '',
        adminPassword: '',
      })
      void queryClient.invalidateQueries({ queryKey: ['platform', 'universities'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  const items = universities.data ?? []

  return (
    <>
      <Card
        title={
          universities.isLoading
            ? 'Universities'
            : `${items.length} universit${items.length === 1 ? 'y' : 'ies'}`
        }
      >
        {universities.isLoading ? (
          <Loading rows={3} />
        ) : items.length === 0 ? (
          <EmptyState title="No universities yet" hint="Add the first one below." />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>University</th>
                  <th>Public address</th>
                  <th>Administrator</th>
                  <th>Listed</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((university) => (
                  <tr key={university.id}>
                    <td>{university.name}</td>
                    <td className="mono small">/u/{university.slug}</td>
                    <td className="small">
                      {university.adminEmail ?? <span className="muted">none</span>}
                    </td>
                    <td>
                      {university.published ? (
                        <Badge kind="success">Published</Badge>
                      ) : (
                        <Badge kind="warning">Draft</Badge>
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

      <Card title="Add a university">
        <form onSubmit={submit} noValidate>
          <Alert kind="info">
            The university starts unlisted. Its administrator signs in with the email address
            below, adds their departments, and then you can publish it.
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
    <Card title="Site branding">
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

      <div className="grid grid-2" style={{ marginTop: '1.2rem' }}>
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

type BugReport = {
  id: number
  title: string
  description: string
  reportedBy: string
  createdAt: string
  status: string
}

function BugReports() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

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

  const items = bugs.data ?? []

  return (
    <Card title={bugs.isLoading ? 'Bug reports' : `${items.length} bug report${items.length === 1 ? '' : 's'}`}>
      {bugs.isLoading ? (
        <Loading rows={3} />
      ) : items.length === 0 ? (
        <EmptyState title="Nothing reported" hint="Reports filed from the app appear here." />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Report</th>
                <th>From</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((bug) => (
                <tr key={bug.id}>
                  <td>
                    <strong>{bug.title}</strong>
                    <p className="small muted">{bug.description}</p>
                  </td>
                  <td className="small">{bug.reportedBy}</td>
                  <td>
                    <Badge kind={bug.status === 'RESOLVED' ? 'success' : 'warning'}>
                      {bug.status}
                    </Badge>
                  </td>
                  <td>
                    <div className="row">
                      <button
                        className="btn btn-secondary btn-sm"
                        disabled={setStatus.isPending}
                        onClick={() => setStatus.mutate({ id: bug.id, status: 'REVIEWED' })}
                      >
                        Reviewed
                      </button>
                      <button
                        className="btn btn-sm"
                        disabled={setStatus.isPending}
                        onClick={() => setStatus.mutate({ id: bug.id, status: 'RESOLVED' })}
                      >
                        Resolved
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

function Broadcast() {
  const { notify, reportError } = useToast()
  const [subject, setSubject] = useState('')
  const [content, setContent] = useState('')
  const [confirmed, setConfirmed] = useState(false)

  const send = useMutation({
    mutationFn: () => api.post<{ successCount: number; failCount: number }>(
      '/api/master/send-email',
      { subject, content },
    ),
    onSuccess: (result) => {
      notify(`Sent to ${result.successCount} recipients (${result.failCount} failed)`, 'success')
      setSubject('')
      setContent('')
      setConfirmed(false)
    },
    onError: (error) => reportError(error),
  })

  return (
    <Card title="Email everyone">
      <form
        onSubmit={(event) => {
          event.preventDefault()
          send.mutate()
        }}
        noValidate
      >
        <Alert kind="info">
          This reaches every account on the platform, across every university. It cannot be
          undone once sent.
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
            rows={6}
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
            I understand this emails every account on LearnX.
          </label>
        </div>

        <button
          className="btn"
          type="submit"
          disabled={send.isPending || !confirmed || !subject || !content}
        >
          {send.isPending ? 'Sending…' : 'Send to everyone'}
        </button>
      </form>
    </Card>
  )
}
