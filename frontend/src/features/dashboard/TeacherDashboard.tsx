import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useCurrentUser } from '@/lib/session'
import { countdownTo, formatDateTime, formatTime } from '@/lib/format'
import type { StudyResource } from '@/lib/types'
import { Badge, Card, EmptyState, Loading, PageHeader } from '@/components/ui'
import StatTile from './StatTile'
import {
  firstName,
  partOfDay,
  todaysClassesFrom,
  upcomingTestsFrom,
  useClassTests,
  useRoutine,
} from './queries'

/** What a teacher opens the app for: today's teaching, and what is waiting on them. */
export default function TeacherDashboard() {
  const user = useCurrentUser()

  const routine = useRoutine()
  const classTests = useClassTests()
  const pendingNotes = useQuery({
    queryKey: ['pendingResources'],
    queryFn: () => api.get<StudyResource[]>('/api/resources/pending'),
  })

  const todaysClasses = todaysClassesFrom(routine.data ?? [])
  const upcomingTests = upcomingTestsFrom(classTests.data ?? [])
  const awaitingApproval = pendingNotes.data ?? []

  return (
    <>
      <PageHeader
        title={`Good ${partOfDay()}, ${firstName(user.fullName)}`}
        description={
          awaitingApproval.length > 0
            ? `${awaitingApproval.length} note${awaitingApproval.length === 1 ? '' : 's'} waiting for your approval.`
            : 'Nothing is waiting on you.'
        }
      />

      <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
        <StatTile
          icon="calendar"
          value={todaysClasses.length}
          label="Classes today"
          hint={
            todaysClasses.length === 0
              ? 'Nothing on the routine today'
              : `First at ${formatTime(todaysClasses[0].startTime)}`
          }
        />
        <StatTile
          icon="clock"
          value={upcomingTests.length}
          label="Upcoming class tests"
          hint={
            upcomingTests.length === 0
              ? 'None scheduled yet'
              : `Next in ${countdownTo(upcomingTests[0].dateTime) || 'moments'}`
          }
        />
        <StatTile
          icon="shield"
          value={pendingNotes.isLoading ? '—' : awaitingApproval.length}
          label="Notes to approve"
          hint={awaitingApproval.length === 0 ? 'The queue is clear' : 'Waiting on you'}
          tone={awaitingApproval.length > 0 ? 'warning' : undefined}
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
                      {[item.className, item.roomNo].filter(Boolean).join(' · ') || '—'}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title="Notes awaiting approval"
          actions={
            <Link className="btn btn-secondary btn-sm" to="/moderation">
              Review
            </Link>
          }
        >
          {pendingNotes.isLoading ? (
            <Loading />
          ) : awaitingApproval.length === 0 ? (
            <EmptyState icon="shield" title="Nothing to review" hint="Uploaded notes wait here for your approval." />
          ) : (
            <div>
              {awaitingApproval.slice(0, 5).map((note) => (
                <div className="timeline-item" key={note.id}>
                  <div>
                    <div className="slot-course">{note.title}</div>
                    <div className="small muted">{note.courseName}</div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Upcoming class tests">
          {classTests.isLoading ? (
            <Loading />
          ) : upcomingTests.length === 0 ? (
            <EmptyState icon="clock" title="Nothing scheduled" hint="No class tests are coming up." />
          ) : (
            <div>
              {upcomingTests.map((test) => (
                <div className="timeline-item" key={test.id}>
                  <div className="timeline-time">
                    <Badge kind="warning">{countdownTo(test.dateTime) || 'now'}</Badge>
                  </div>
                  <div>
                    <div className="slot-course">{test.courseName}</div>
                    <div className="small muted">{formatDateTime(test.dateTime)}</div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Your tools">
          <div className="row">
            <Link className="btn btn-secondary btn-sm" to="/exams">
              Exams
            </Link>
            <Link className="btn btn-secondary btn-sm" to="/gradebook">
              Gradebook
            </Link>
            <Link className="btn btn-secondary btn-sm" to="/announcements">
              Announcements
            </Link>
            <Link className="btn btn-secondary btn-sm" to="/notes">
              Notes library
            </Link>
          </div>
        </Card>
      </div>
    </>
  )
}
