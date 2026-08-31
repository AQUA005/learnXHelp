import { Link } from 'react-router-dom'
import { useCurrentUser } from '@/lib/session'
import { countdownTo, formatDateTime, formatTime } from '@/lib/format'
import { Badge, Card, EmptyState, Loading, PageHeader } from '@/components/ui'
import StatTile from './StatTile'
import {
  averagePercentage,
  firstName,
  partOfDay,
  summaryLine,
  todaysClassesFrom,
  upcomingTestsFrom,
  useAnnouncements,
  useClassTests,
  usePerformance,
  useRoutine,
} from './queries'

/** What a student needs on opening the app: today, what is coming, how they are doing. */
export default function StudentDashboard() {
  const user = useCurrentUser()

  const routine = useRoutine()
  const classTests = useClassTests()
  const announcements = useAnnouncements()
  const performance = usePerformance()

  const todaysClasses = todaysClassesFrom(routine.data ?? [])
  const upcomingTests = upcomingTestsFrom(classTests.data ?? [])
  const recentAnnouncements = (announcements.data ?? []).slice(0, 4)

  const results = performance.data ?? []
  const average = averagePercentage(results)
  const scored = results.filter((stat) => stat.maxMarks > 0)

  return (
    <>
      <PageHeader
        title={`Good ${partOfDay()}, ${firstName(user.fullName)}`}
        description={summaryLine(todaysClasses.length, upcomingTests.length)}
      />

      <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
        <StatTile
          icon="calendar"
          value={todaysClasses.length}
          label="Classes today"
          hint={
            todaysClasses.length === 0
              ? 'Nothing on the routine today'
              : `Next at ${formatTime(todaysClasses[0].startTime)}`
          }
        />
        <StatTile
          icon="clock"
          value={upcomingTests.length}
          label="Upcoming class tests"
          hint={
            upcomingTests.length === 0
              ? 'None announced yet'
              : `Next in ${countdownTo(upcomingTests[0].dateTime) || 'moments'}`
          }
          tone={upcomingTests.length > 0 ? 'warning' : undefined}
        />
        <StatTile
          icon="chart"
          value={average == null ? '—' : `${average}%`}
          label="Average result"
          hint={
            average == null
              ? 'No marks published yet'
              : `Across ${scored.length} assessment${scored.length === 1 ? '' : 's'}`
          }
        />
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
            <EmptyState icon="calendar" title="No classes today" hint="Nothing on your routine for today." />
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
            <EmptyState icon="clock" title="Nothing scheduled" hint="No class tests have been announced." />
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
            <EmptyState icon="megaphone" title="Nothing new" hint="Announcements from your class appear here." />
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
            <EmptyState icon="chart" title="No results yet" hint="Marks appear here once a teacher publishes them." />
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

