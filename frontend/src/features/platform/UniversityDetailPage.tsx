import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { Alert, Badge, Card, Field, Loading, PageHeader } from '@/components/ui'
import { ImageUpload } from './PlatformPage'
import type { ConsoleUniversity } from './types'

/** One university: its profile, its logo, whether it is listed, and removing it. */
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
      notify(saved.published ? 'Published' : 'Taken off the home page', 'success')
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
          Back to the platform
        </Link>
        {current.published ? <Badge kind="success">Published</Badge> : <Badge kind="warning">Draft</Badge>}
      </div>

      <Card title="Listing">
        <p className="small muted">
          A published university appears on the public home page and is open for sign-ups. It
          needs at least one department first, or the sign-up form has nothing to offer.
        </p>
        <button
          className={current.published ? 'btn btn-secondary' : 'btn'}
          disabled={setPublished.isPending}
          onClick={() => setPublished.mutate(!current.published)}
        >
          {current.published ? 'Take off the home page' : 'Publish'}
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
          themselves out. If no account has that address, one is created.
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
 * note, routine and result belonging to it, and there is no undo.
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
        This permanently removes every account, class, routine, note, exam and result belonging
        to {university.name}. It cannot be undone.
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
