import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useCurrentUser } from '@/lib/session'
import { useBranding } from '@/lib/branding'
import { Badge, Card, EmptyState, Loading, PageHeader } from '@/components/ui'
import type { ConsoleUniversity } from '@/features/platform/types'
import { firstName, partOfDay } from './queries'

type BugReport = { id: number; title: string; status: string; reportedBy: string }

/**
 * The platform owner's home screen.
 *
 * Every query here is platform-level. That is the point: a platform owner
 * belongs to no university, so the tenant-scoped endpoints behind the other
 * dashboards answer 403 for them — which is exactly what they used to be shown.
 */
export default function PlatformDashboard() {
  const user = useCurrentUser()
  const branding = useBranding()

  const universities = useQuery({
    queryKey: ['platform', 'universities'],
    queryFn: () => api.get<ConsoleUniversity[]>('/api/master/universities'),
  })
  const bugs = useQuery({
    queryKey: ['platform', 'bugs'],
    queryFn: () => api.get<BugReport[]>('/api/master/bugs'),
  })

  const all = universities.data ?? []
  const published = all.filter((university) => university.published)
  const drafts = all.filter((university) => !university.published)
  const openBugs = (bugs.data ?? []).filter((bug) => bug.status !== 'RESOLVED')

  return (
    <>
      <PageHeader
        title={`Good ${partOfDay()}, ${firstName(user.fullName)}`}
        description={`You are the owner of ${branding.siteName}.`}
      />

      <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
        <Card>
          <div className="stat">
            <span className="stat-value mono">
              {universities.isLoading ? '—' : published.length}
            </span>
            <span className="stat-label">Published universities</span>
          </div>
        </Card>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{universities.isLoading ? '—' : drafts.length}</span>
            <span className="stat-label">Awaiting publication</span>
          </div>
        </Card>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{bugs.isLoading ? '—' : openBugs.length}</span>
            <span className="stat-label">Open bug reports</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-2">
        <Card
          title="Universities"
          actions={
            <Link className="btn btn-secondary btn-sm" to="/platform">
              Manage
            </Link>
          }
        >
          {universities.isLoading ? (
            <Loading />
          ) : all.length === 0 ? (
            <EmptyState title="No universities yet" hint="Add the first from the platform screen." />
          ) : (
            <div>
              {all.slice(0, 6).map((university) => (
                <div className="timeline-item" key={university.id}>
                  <div className="timeline-time">
                    {university.published ? (
                      <Badge kind="success">Live</Badge>
                    ) : (
                      <Badge kind="warning">Draft</Badge>
                    )}
                  </div>
                  <div>
                    <Link className="slot-course" to={`/platform/universities/${university.id}`}>
                      {university.name}
                    </Link>
                    <div className="small muted mono">/u/{university.slug}</div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title="Open bug reports"
          actions={
            <Link className="btn btn-secondary btn-sm" to="/platform">
              Triage
            </Link>
          }
        >
          {bugs.isLoading ? (
            <Loading />
          ) : openBugs.length === 0 ? (
            <EmptyState title="Nothing outstanding" />
          ) : (
            <div>
              {openBugs.slice(0, 6).map((bug) => (
                <div className="timeline-item" key={bug.id}>
                  <div>
                    <div className="slot-course">{bug.title}</div>
                    <div className="small muted">{bug.reportedBy}</div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </>
  )
}
