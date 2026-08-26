import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useCurrentUser } from '@/lib/session'
import { countdownTo, formatDateTime, formatTime } from '@/lib/format'
import type { StudyResource } from '@/lib/types'
import { Badge, Card, EmptyState, Loading, PageHeader } from '@/components/ui'
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
            <span className="stat-value mono">
              {pendingNotes.isLoading ? '—' : awaitingApproval.length}
            </span>
            <span className="stat-label">Notes to approve</span>
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
            <EmptyState title="No classes today" />
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
            <EmptyState title="Nothing to review" hint="Uploaded notes appear here first." />
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
            <EmptyState title="Nothing scheduled" />
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
