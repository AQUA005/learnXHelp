import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useCurrentUser } from '@/lib/session'
import { formatDateTime } from '@/lib/format'
import { Card, EmptyState, Loading, PageHeader } from '@/components/ui'
import { firstName, partOfDay } from './queries'

type PendingUser = { id: number; fullName: string; role: string; department: string }
type ClassGroup = { id: number; className: string; studentsCount: number }
type AuditEntry = {
  id: number
  entityType: string
  action: string
  changedBy: string
  timestamp: string
  details: string | null
}

/**
 * What a university administrator opens the app for: who is waiting, what the
 * university looks like, and what changed recently.
 *
 * Deliberately not the student dashboard. An administrator has no class of their
 * own, so a countdown to their next class test means nothing to them.
 */
export default function AdminDashboard() {
  const user = useCurrentUser()

  const pending = useQuery({
    queryKey: ['pending-users'],
    queryFn: () => api.get<PendingUser[]>('/api/admin/pending'),
  })
  const classes = useQuery({
    queryKey: ['classes'],
    queryFn: () => api.get<ClassGroup[]>('/api/admin/classes'),
  })
  const audit = useQuery({
    queryKey: ['audit-logs'],
    queryFn: () => api.get<AuditEntry[]>('/api/schedule/audit-logs'),
  })

  const waiting = pending.data ?? []
  const groups = classes.data ?? []
  const students = groups.reduce((sum, group) => sum + (group.studentsCount ?? 0), 0)

  return (
    <>
      <PageHeader
        title={`Good ${partOfDay()}, ${firstName(user.fullName)}`}
        description={
          user.university
            ? `Administering ${user.university.name}.`
            : 'Your account is not attached to a university.'
        }
      />

      <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{pending.isLoading ? '—' : waiting.length}</span>
            <span className="stat-label">Awaiting approval</span>
          </div>
        </Card>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{classes.isLoading ? '—' : groups.length}</span>
            <span className="stat-label">Class groups</span>
          </div>
        </Card>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{classes.isLoading ? '—' : students}</span>
            <span className="stat-label">Students enrolled</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-2">
        <Card
          title="Waiting for approval"
          actions={
            <Link className="btn btn-secondary btn-sm" to="/admin">
              Review
            </Link>
          }
        >
          {pending.isLoading ? (
            <Loading />
          ) : waiting.length === 0 ? (
            <EmptyState title="Nothing to approve" hint="New sign-ups appear here." />
          ) : (
            <div>
              {waiting.slice(0, 5).map((account) => (
                <div className="timeline-item" key={account.id}>
                  <div>
                    <div className="slot-course">{account.fullName}</div>
                    <div className="small muted">
                      {account.role} · {account.department}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title="Class groups"
          actions={
            <Link className="btn btn-secondary btn-sm" to="/admin">
              Manage
            </Link>
          }
        >
          {classes.isLoading ? (
            <Loading />
          ) : groups.length === 0 ? (
            <EmptyState title="No class groups yet" />
          ) : (
            <div>
              {groups.slice(0, 6).map((group) => (
                <div className="timeline-item" key={group.id}>
                  <div className="timeline-time mono">{group.studentsCount ?? 0}</div>
                  <div>
                    <Link className="slot-course" to={`/admin/classes/${group.id}`}>
                      {group.className}
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Recent changes">
          {audit.isLoading ? (
            <Loading />
          ) : (audit.data ?? []).length === 0 ? (
            <EmptyState title="Nothing recorded yet" />
          ) : (
            <div>
              {(audit.data ?? []).slice(0, 6).map((entry) => (
                <div className="timeline-item" key={entry.id}>
                  <div>
                    <div className="slot-course">
                      {entry.action} {entry.entityType}
                    </div>
                    <div className="small muted">
                      {entry.changedBy} · {formatDateTime(entry.timestamp)}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Your tools">
          <div className="row">
            <Link className="btn btn-secondary btn-sm" to="/admin">
              Administration
            </Link>
            <Link className="btn btn-secondary btn-sm" to="/schedule">
              Class routine
            </Link>
            <Link className="btn btn-secondary btn-sm" to="/announcements">
              Announcements
            </Link>
            <Link className="btn btn-secondary btn-sm" to="/moderation">
              Note approvals
            </Link>
          </div>
        </Card>
      </div>
    </>
  )
}
