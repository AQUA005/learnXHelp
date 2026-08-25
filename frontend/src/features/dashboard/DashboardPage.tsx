import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useCurrentUser } from '@/lib/session'
import { countdownTo, formatDateTime, formatTime, todayName } from '@/lib/format'
import type { Announcement, ClassTest, PerformanceStat, RoutineItem } from '@/lib/types'
import { Badge, Card, EmptyState, Loading, PageHeader } from '@/components/ui'

/** What a student needs on opening the app: today, what is coming, how they are doing. */
export default function DashboardPage() {
  const user = useCurrentUser()

  const routine = useQuery({
    queryKey: ['routine'],
    queryFn: () => api.get<RoutineItem[]>('/api/schedule/routine'),
  })
  const classTests = useQuery({
    queryKey: ['classTests'],
    queryFn: () => api.get<ClassTest[]>('/api/schedule/ct'),
  })
  const announcements = useQuery({
    queryKey: ['announcements'],
    queryFn: () => api.get<Announcement[]>('/api/announcements'),
  })
  const performance = useQuery({
    queryKey: ['performance'],
    queryFn: () => api.get<PerformanceStat[]>('/api/dashboard/performance'),
  })

  const today = todayName()
  const todaysClasses = (routine.data ?? [])
    .filter((item) => item.dayOfWeek?.toUpperCase() === today)
    .sort((a, b) => a.startTime.localeCompare(b.startTime))

  const upcomingTests = (classTests.data ?? [])
    .filter((test) => new Date(test.dateTime).getTime() > Date.now())
    .sort((a, b) => a.dateTime.localeCompare(b.dateTime))
    .slice(0, 4)

  const recentAnnouncements = (announcements.data ?? []).slice(0, 4)

  const average = averagePercentage(performance.data ?? [])

  return (
    <>
      <PageHeader
        title={`Good ${partOfDay()}, ${firstName(user.fullName)}`}
        description={summaryLine(todaysClasses.length, upcomingTests.length)}
      />

      <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{todaysClasses.length}</span>
            <span className="stat-label">Classes today</span>
          </div>
        </Card>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{upcomingTests.length}</span>
            <span className="stat-label">Upcoming class tests</span>
          </div>
        </Card>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{average == null ? '—' : `${average}%`}</span>
            <span className="stat-label">Average result</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-2">
        <Card
          title="Today"
          actions={
            <Link className="btn btn-secondary btn-sm" to="/schedule">
              Full routine
            </Link>
          }
        >
          {routine.isLoading ? (
            <Loading />
          ) : todaysClasses.length === 0 ? (
            <EmptyState title="No classes today" hint="Enjoy the break." />
          ) : (
            <div>
              {todaysClasses.map((item) => (
                <div className="timeline-item" key={item.id}>
                  <div className="timeline-time mono">{formatTime(item.startTime)}</div>
                  <div>
                    <div className="slot-course">{item.courseName}</div>
                    <div className="small muted">
                      {[item.roomNo, item.teacherName].filter(Boolean).join(' · ') || '—'}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Coming up">
          {classTests.isLoading ? (
            <Loading />
          ) : upcomingTests.length === 0 ? (
            <EmptyState title="Nothing scheduled" hint="No class tests are coming up." />
          ) : (
            <div>
              {upcomingTests.map((test) => (
                <div className="timeline-item" key={test.id}>
                  <div className="timeline-time">
                    <Badge kind="warning">{countdownTo(test.dateTime) || 'now'}</Badge>
                  </div>
                  <div>
                    <div className="slot-course">{test.courseName}</div>
                    <div className="small muted">
                      {formatDateTime(test.dateTime)}
                      {test.topic ? ` · ${test.topic}` : ''}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title="Announcements"
          actions={
            <Link className="btn btn-secondary btn-sm" to="/announcements">
              See all
            </Link>
          }
        >
          {announcements.isLoading ? (
            <Loading />
          ) : recentAnnouncements.length === 0 ? (
            <EmptyState title="Nothing new" />
          ) : (
            <div>
              {recentAnnouncements.map((item) => (
                <div className="timeline-item" key={item.id}>
                  <div>
                    <div className="slot-course">{item.title}</div>
                    <div className="small muted">
                      {item.createdBy} · {formatDateTime(item.createdAt)}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title="Recent results"
          actions={
            <Link className="btn btn-secondary btn-sm" to="/performance">
              Details
            </Link>
          }
        >
          {performance.isLoading ? (
            <Loading />
          ) : (performance.data ?? []).length === 0 ? (
            <EmptyState title="No results yet" hint="Marks appear here once published." />
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Assessment</th>
                    <th>Score</th>
                  </tr>
                </thead>
                <tbody>
                  {(performance.data ?? []).slice(0, 5).map((stat) => (
                    <tr key={stat.id}>
                      <td>
                        {stat.assessmentName}
                        <div className="small muted">{stat.courseName}</div>
                      </td>
                      <td className="mono">
                        {stat.marksObtained} / {stat.maxMarks}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </>
  )
}

function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] || 'there'
}

function partOfDay(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'morning'
  if (hour < 17) return 'afternoon'
  return 'evening'
}

function summaryLine(classes: number, tests: number): string {
  if (classes === 0 && tests === 0) return 'Nothing scheduled at the moment.'
  const parts: string[] = []
  if (classes > 0) parts.push(`${classes} class${classes === 1 ? '' : 'es'} today`)
  if (tests > 0) parts.push(`${tests} class test${tests === 1 ? '' : 's'} coming up`)
  return `${parts.join(', ')}.`
}

/** Mean percentage across published results, rounded. */
function averagePercentage(stats: PerformanceStat[]): number | null {
  const scored = stats.filter((stat) => stat.maxMarks > 0)
  if (scored.length === 0) return null
  const total = scored.reduce((sum, stat) => sum + (stat.marksObtained / stat.maxMarks) * 100, 0)
  return Math.round(total / scored.length)
}
