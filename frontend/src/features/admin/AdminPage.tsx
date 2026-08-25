import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { formatDateTime } from '@/lib/format'
import type { MetadataOption, PendingUser } from '@/lib/types'
import { Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'

type Tab = 'approvals' | 'classes' | 'metadata' | 'audit'

export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('approvals')

  const tabs: { id: Tab; label: string }[] = [
    { id: 'approvals', label: 'Account approvals' },
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

type ClassGroup = {
  id: number
  className: string
  batch: string
  department: string
  section: string
  studentsCount?: number
  crUsername?: string | null
}

function Classes() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const classes = useQuery({
    queryKey: ['classes'],
    queryFn: () => api.get<ClassGroup[]>('/api/admin/classes'),
  })

  const promote = useMutation({
    mutationFn: (id: number) => api.post<{ message: string }>(`/api/admin/classes/${id}/promote`),
    onSuccess: (result) => {
      notify(result.message, 'success')
      void queryClient.invalidateQueries({ queryKey: ['classes'] })
    },
    onError: (error) => reportError(error),
  })

  const rollback = useMutation({
    mutationFn: (id: number) =>
      api.post<{ message: string }>(`/api/admin/classes/${id}/rollback-promotion`),
    onSuccess: (result) => {
      notify(result.message, 'success')
      void queryClient.invalidateQueries({ queryKey: ['classes'] })
    },
    onError: (error) => reportError(error),
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
                  <td>{group.className}</td>
                  <td className="mono">{group.studentsCount ?? 0}</td>
                  <td>{group.crUsername ?? <span className="muted">none</span>}</td>
                  <td>
                    <div className="row">
                      <button
                        className="btn btn-sm"
                        onClick={() => promote.mutate(group.id)}
                        disabled={promote.isPending}
                      >
                        Promote a semester
                      </button>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => rollback.mutate(group.id)}
                        disabled={rollback.isPending}
                      >
                        Undo
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
