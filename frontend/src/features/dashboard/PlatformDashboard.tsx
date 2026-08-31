import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useCurrentUser } from '@/lib/session'
import { useBranding } from '@/lib/branding'
import { Badge, Card, EmptyState, Loading, PageHeader } from '@/components/ui'
import StatTile from './StatTile'
import type { BugReport, ConsoleUniversity } from '@/features/platform/types'
import { platformTabPath } from '@/features/platform/tabs'
import { firstName, partOfDay } from './queries'

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
  const people = all.reduce((total, university) => total + university.userCount, 0)

  return (
    <>
      <PageHeader
        title={`Good ${partOfDay()}, ${firstName(user.fullName)}`}
        description={`You are the owner of ${branding.siteName}.`}
      />

      <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
        <StatTile
          icon="platform"
          value={universities.isLoading ? '—' : published.length}
          label="Listed universities"
          hint={published.length === 0 ? 'None are public yet' : 'Open for sign-ups'}
        />
        <StatTile
          icon="classes"
          value={universities.isLoading ? '—' : drafts.length}
          label="Hidden universities"
          hint={drafts.length === 0 ? 'None waiting' : 'Set up but not yet listed'}
          tone={drafts.length > 0 ? 'warning' : undefined}
        />
        <StatTile
          icon="people"
          value={universities.isLoading ? '—' : people}
          label="Accounts on LearnX"
          hint={
            all.length === 0
              ? 'Nobody yet'
              : `Across ${all.length} universit${all.length === 1 ? 'y' : 'ies'}`
          }
        />
        <StatTile
          icon="bug"
          value={bugs.isLoading ? '—' : openBugs.length}
          label="Open bug reports"
          hint={openBugs.length === 0 ? 'Nothing outstanding' : 'Reported and not yet resolved'}
          tone={openBugs.length > 0 ? 'warning' : undefined}
        />
      </div>

      <div className="grid grid-2">
        <Card
          title="Universities"
          actions={
            <Link className="btn btn-secondary btn-sm" to={platformTabPath('universities')}>
              Manage
            </Link>
          }
        >
          {universities.isLoading ? (
            <Loading />
          ) : all.length === 0 ? (
            <EmptyState icon="platform" title="No universities yet" hint="Add the first from the platform screen." />
          ) : (
            <div>
              {all.slice(0, 6).map((university) => (
                <div className="timeline-item" key={university.id}>
                  <div className="timeline-time">
                    {university.published ? (
                      <Badge kind="success">Listed</Badge>
                    ) : (
                      <Badge kind="warning">Hidden</Badge>
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
            <Link className="btn btn-secondary btn-sm" to={platformTabPath('bugs')}>
              Triage
            </Link>
          }
        >
          {bugs.isLoading ? (
            <Loading />
          ) : openBugs.length === 0 ? (
            <EmptyState
              icon="bug"
              title="Nothing outstanding"
              hint="Reports from any university land here."
            />
          ) : (
            <div>
              {openBugs.slice(0, 6).map((bug) => (
                <div className="timeline-item" key={bug.id}>
                  <div>
                    <div className="slot-course">{bug.title}</div>
                    <div className="small muted">
                      {bug.reportedBy ?? 'Unknown'} · {bug.universityName ?? 'LearnX'}
                    </div>
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
