import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { formatDateTime } from '@/lib/format'
import type { MetadataOption, PendingUser } from '@/lib/types'
import { Alert, Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'

type Tab = 'approvals' | 'people' | 'classes' | 'metadata' | 'audit'

export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('approvals')

  const tabs: { id: Tab; label: string }[] = [
    { id: 'approvals', label: 'Account approvals' },
    { id: 'people', label: 'People' },
    { id: 'classes', label: 'Classes' },
    { id: 'metadata', label: 'Dropdown options' },
    { id: 'audit', label: 'Change history' },
  ]

  return (
    <>
      <PageHeader title="Administration" description="Approvals, class groups and reference data." />

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

      {tab === 'approvals' && <Approvals />}
      {tab === 'people' && <People />}
      {tab === 'classes' && <Classes />}
      {tab === 'metadata' && <Metadata />}
      {tab === 'audit' && <AuditTrail />}
    </>
  )
}

function Approvals() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const pending = useQuery({
    queryKey: ['pending-users'],
    queryFn: () => api.get<PendingUser[]>('/api/admin/pending'),
  })

  const approve = useMutation({
    mutationFn: (id: number) => api.post(`/api/admin/approve/${id}`),
    onSuccess: () => {
      notify('Account approved', 'success')
      void queryClient.invalidateQueries({ queryKey: ['pending-users'] })
    },
    onError: (error) => reportError(error),
  })

  const reject = useMutation({
    mutationFn: (id: number) => api.del(`/api/admin/reject/${id}`),
    onSuccess: () => {
      notify('Request rejected', 'success')
      void queryClient.invalidateQueries({ queryKey: ['pending-users'] })
    },
    onError: (error) => reportError(error),
  })

  const items = pending.data ?? []

  return (
    <Card title={pending.isLoading ? 'Awaiting approval' : `${items.length} awaiting approval`}>
      {pending.isLoading ? (
        <Loading rows={3} />
      ) : items.length === 0 ? (
        <EmptyState title="Nothing to approve" hint="New sign-ups appear here." />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Username</th>
                <th>Email</th>
                <th>Role</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((account) => (
                <tr key={account.id}>
                  <td>{account.fullName}</td>
                  <td>{account.username}</td>
                  <td className="small">{account.email}</td>
                  <td>
                    <Badge>{account.role}</Badge>
                  </td>
                  <td>
                    <div className="row">
                      <button
                        className="btn btn-sm"
                        onClick={() => approve.mutate(account.id)}
                        disabled={approve.isPending}
                      >
                        Approve
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => reject.mutate(account.id)}
                        disabled={reject.isPending}
                      >
                        Reject
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

type Person = {
  id: number
  username: string
  fullName: string
  email: string
  role: string
  approved: boolean
}

/**
 * Everyone at the university, and the way back in for someone who cannot sign
 * in. Self-service recovery needs working email; where that is unavailable this
 * is the only route, so it lives with the everyday administration screens
 * rather than being hidden away.
 */
function People() {
  const { notify, reportError } = useToast()
  const [search, setSearch] = useState('')
  const [issued, setIssued] = useState<{ email: string; password: string } | null>(null)

  const people = useQuery({
    queryKey: ['people'],
    queryFn: () => api.get<Person[]>('/api/admin/users'),
  })

  const reset = useMutation({
    mutationFn: (id: number) =>
      api.post<{ message: string; email: string; password: string }>(
        `/api/admin/users/${id}/reset-password`,
        {},
      ),
    onSuccess: (result) => {
      setIssued({ email: result.email, password: result.password })
      notify(result.message, 'success')
    },
    onError: (error) => reportError(error),
  })

  const needle = search.trim().toLowerCase()
  const visible = (people.data ?? []).filter(
    (person) =>
      !needle ||
      person.fullName.toLowerCase().includes(needle) ||
      person.username.toLowerCase().includes(needle) ||
      person.email.toLowerCase().includes(needle),
  )

  return (
    <>
      {issued && (
        <Card title="New password">
          <Alert kind="success">
            Give this to <strong>{issued.email}</strong> — the address they sign in with — and
            ask them to change it once they have signed in. It is shown only now and cannot be looked up again.
          </Alert>
          <p className="mono" style={{ fontSize: '1.3rem', letterSpacing: '0.05em', margin: 0 }}>
            {issued.password}
          </p>
          <div className="row row-end" style={{ marginTop: '0.8rem' }}>
            <button className="btn btn-secondary btn-sm" onClick={() => setIssued(null)}>
              Done
            </button>
          </div>
        </Card>
      )}

      <Card
        title={people.isLoading ? 'Everyone' : `${visible.length} of ${(people.data ?? []).length}`}
        actions={
          <input
            placeholder="Search by name, username or email"
            aria-label="Search people"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      >
        {people.isLoading ? (
          <Loading rows={4} />
        ) : visible.length === 0 ? (
          <EmptyState title="Nobody found" hint="Try a different search." />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visible.map((person) => (
                  <tr key={person.id}>
                    <td>
                      {person.fullName}
                      {!person.approved && (
                        <>
                          {' '}
                          <Badge kind="warning">awaiting approval</Badge>
                        </>
                      )}
                    </td>
                    <td className="mono">{person.username}</td>
                    <td className="small">{person.email}</td>
                    <td>
                      <Badge>{person.role}</Badge>
                    </td>
                    <td>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => reset.mutate(person.id)}
                        disabled={reset.isPending}
                      >
                        Reset password
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}

/**
 * A class group as `/api/admin/classes` lists it.
 *
 * `className` and the representative fields are genuinely present now. The
 * server used to omit `className` entirely — so the Class column rendered
 * blank — and sent the string "None Assigned" rather than null for an
 * unassigned representative, so the fallback below never showed.
 */
type ClassGroup = {
  id: number
  className: string
  batch: string
  department: string
  section: string
  semester: string
  studentsCount: number
  crUsername: string | null
  crFullName: string | null
}

/**
 * The class groups, as a way in rather than a place to act.
 *
 * Promotion and rollback used to sit on each row here, one click away and with
 * nothing to say which class was about to move. They live on the class's own
 * screen now, next to the roster they affect.
 */
function Classes() {
  const classes = useQuery({
    queryKey: ['classes'],
    queryFn: () => api.get<ClassGroup[]>('/api/admin/classes'),
  })

  const items = classes.data ?? []

  return (
    <Card title={classes.isLoading ? 'Class groups' : `${items.length} class group${items.length === 1 ? '' : 's'}`}>
      {classes.isLoading ? (
        <Loading rows={3} />
      ) : items.length === 0 ? (
        <EmptyState title="No class groups yet" />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Class</th>
                <th>Students</th>
                <th>Representative</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((group) => (
                <tr key={group.id}>
                  <td>
                    <Link to={`/admin/classes/${group.id}`}>{group.className}</Link>
                  </td>
                  <td className="mono">{group.studentsCount ?? 0}</td>
                  <td>{group.crFullName ?? <span className="muted">none</span>}</td>
                  <td>
                    <Link className="btn btn-secondary btn-sm" to={`/admin/classes/${group.id}`}>
                      Open
                    </Link>
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

const METADATA_TYPES = ['DEPARTMENT', 'SEMESTER', 'BATCH', 'SECTION', 'DESIGNATION']

function Metadata() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [type, setType] = useState(METADATA_TYPES[0])
  const [value, setValue] = useState('')

  const options = useQuery({
    queryKey: ['metadata'],
    queryFn: () => api.get<MetadataOption[]>('/api/metadata'),
  })

  const add = useMutation({
    mutationFn: () => api.post<MetadataOption>('/api/metadata', { type, value }),
    onSuccess: () => {
      notify('Option added', 'success')
      setValue('')
      void queryClient.invalidateQueries({ queryKey: ['metadata'] })
    },
    onError: (error) => reportError(error),
  })

  const remove = useMutation({
    mutationFn: (id: number) => api.del(`/api/metadata/${id}`),
    onSuccess: () => {
      notify('Option removed', 'success')
      void queryClient.invalidateQueries({ queryKey: ['metadata'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    add.mutate()
  }

  return (
    <>
      <Card title="Add an option">
        <form onSubmit={submit}>
          <div className="grid grid-2">
            <Field label="List" htmlFor="md-type">
              <select id="md-type" value={type} onChange={(e) => setType(e.target.value)}>
                {METADATA_TYPES.map((entry) => (
                  <option key={entry} value={entry}>
                    {entry.toLowerCase()}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Value" htmlFor="md-value">
              <input id="md-value" required value={value} onChange={(e) => setValue(e.target.value)} />
            </Field>
          </div>
          <button className="btn" type="submit" disabled={add.isPending}>
            Add option
          </button>
        </form>
      </Card>

      {METADATA_TYPES.map((entry) => {
        const forType = (options.data ?? []).filter((option) => option.type === entry)
        if (forType.length === 0) return null
        return (
          <Card key={entry} title={entry.toLowerCase()}>
            <div className="row">
              {forType.map((option) => (
                <span key={option.id} className="row" style={{ gap: '0.3rem' }}>
                  <Badge kind="accent">{option.value}</Badge>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => remove.mutate(option.id)}
                    aria-label={`Remove ${option.value}`}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          </Card>
        )
      })}
    </>
  )
}

type AuditEntry = {
  id: number
  entityType: string
  entityId: number | null
  action: string
  changedBy: string
  timestamp: string
  details: string | null
}

function AuditTrail() {
  const logs = useQuery({
    queryKey: ['audit-logs'],
    queryFn: () => api.get<AuditEntry[]>('/api/schedule/audit-logs'),
  })

  const items = logs.data ?? []

  return (
    <Card title="Changes to the routine and class tests">
      {logs.isLoading ? (
        <Loading rows={4} />
      ) : items.length === 0 ? (
        <EmptyState title="Nothing recorded yet" />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Who</th>
                <th>Action</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {items.slice(0, 100).map((entry) => (
                <tr key={entry.id}>
                  <td className="small">{formatDateTime(entry.timestamp)}</td>
                  <td>{entry.changedBy}</td>
                  <td>
                    <Badge
                      kind={
                        entry.action === 'DELETE'
                          ? 'danger'
                          : entry.action === 'CREATE'
                            ? 'success'
                            : 'accent'
                      }
                    >
                      {entry.action}
                    </Badge>
                  </td>
                  <td className="small">{entry.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
