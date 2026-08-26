import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { formatDateTime, formatTime } from '@/lib/format'
import { Alert, Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'

type Tab = 'roster' | 'courses' | 'routine' | 'promotions'

type ClassMember = {
  id: number
  username: string
  fullName: string
  email: string
  idNo: string | null
  role: string
  approved: boolean
}

type CourseAssignment = {
  id: number
  courseId: number
  courseCode: string
  courseName: string
  credits: number | null
  teacherId: number
  teacherName: string
  teacherDesignation: string | null
}

type RoutineEntry = {
  id: number
  dayOfWeek: string
  startTime: string
  endTime: string
  courseName: string
  teacherName: string | null
  roomNo: string | null
}

type PromotionEntry = {
  id: number
  fromSemester: string
  toSemester: string
  timestamp: string
}

type ClassDetail = {
  id: number
  className: string
  department: string
  batch: string
  section: string
  semester: string
  studentCount: number
  cr: ClassMember | null
  students: ClassMember[]
  courses: CourseAssignment[]
  routine: RoutineEntry[]
  promotions: PromotionEntry[]
}

/** One class group, and everything an administrator does to it. */
export default function ClassDetailPage() {
  const { id = '' } = useParams<{ id: string }>()
  const [tab, setTab] = useState<Tab>('roster')

  const detail = useQuery({
    queryKey: ['class-detail', id],
    queryFn: () => api.get<ClassDetail>(`/api/admin/classes/${id}`),
  })

  if (detail.isLoading) {
    return <Loading rows={5} label="Loading the class" />
  }
  if (!detail.data) {
    return <Alert kind="error">Could not load that class group.</Alert>
  }

  const group = detail.data

  const tabs: { id: Tab; label: string }[] = [
    { id: 'roster', label: 'Roster' },
    { id: 'courses', label: 'Courses & teachers' },
    { id: 'routine', label: 'Routine' },
    { id: 'promotions', label: 'Promotions' },
  ]

  return (
    <>
      <PageHeader
        title={group.className}
        description={`${group.department} · ${group.batch} · ${group.section}`}
      />

      <div className="row" style={{ marginBottom: '1rem' }}>
        <Link className="btn btn-secondary btn-sm" to="/admin">
          Back to administration
        </Link>
      </div>

      <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{group.studentCount}</span>
            <span className="stat-label">Students</span>
          </div>
        </Card>
        <Card>
          <div className="stat">
            <span className="stat-value mono">{group.courses.length}</span>
            <span className="stat-label">Courses assigned</span>
          </div>
        </Card>
        <Card>
          <div className="stat">
            <span className="stat-value">{group.semester}</span>
            <span className="stat-label">Current semester</span>
          </div>
        </Card>
      </div>

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

      {tab === 'roster' && <Roster classId={id} group={group} />}
      {tab === 'courses' && <Courses classId={id} group={group} />}
      {tab === 'routine' && <Routine group={group} />}
      {tab === 'promotions' && <Promotions classId={id} group={group} />}
    </>
  )
}

function Roster({ classId, group }: { classId: string; group: ClassDetail }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const assignCr = useMutation({
    mutationFn: (username: string) =>
      api.post<{ message: string }>(`/api/admin/classes/${classId}/assign-cr`, { username }),
    onSuccess: (result) => {
      notify(result.message, 'success')
      void queryClient.invalidateQueries({ queryKey: ['class-detail', classId] })
      void queryClient.invalidateQueries({ queryKey: ['classes'] })
    },
    onError: (error) => reportError(error),
  })

  return (
    <Card
      title={`${group.students.length} student${group.students.length === 1 ? '' : 's'}`}
      actions={
        group.cr ? (
          <span className="small muted">Representative: {group.cr.fullName}</span>
        ) : (
          <Badge kind="warning">No representative</Badge>
        )
      }
    >
      {group.students.length === 0 ? (
        <EmptyState
          title="Nobody in this class yet"
          hint="Students join once an administrator approves their account."
        />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>ID number</th>
                <th>Email</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {group.students.map((student) => (
                <tr key={student.id}>
                  <td>
                    {student.fullName}
                    {student.role === 'CR' && (
                      <>
                        {' '}
                        <Badge kind="accent">CR</Badge>
                      </>
                    )}
                  </td>
                  <td className="mono small">{student.idNo ?? '—'}</td>
                  <td className="small">{student.email}</td>
                  <td>
                    {student.approved ? (
                      <Badge kind="success">Approved</Badge>
                    ) : (
                      <Badge kind="warning">Pending</Badge>
                    )}
                  </td>
                  <td>
                    {student.role !== 'CR' && (
                      <button
                        className="btn btn-secondary btn-sm"
                        disabled={assignCr.isPending}
                        onClick={() => assignCr.mutate(student.username)}
                      >
                        Make representative
                      </button>
                    )}
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

type Course = { id: number; code: string; name: string }
type Teacher = { id: number; fullName: string; designation: string | null }

function Courses({ classId, group }: { classId: string; group: ClassDetail }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [courseId, setCourseId] = useState('')
  const [teacherId, setTeacherId] = useState('')

  const courses = useQuery({
    queryKey: ['courses'],
    queryFn: () => api.get<Course[]>('/api/admin/courses'),
  })
  const teachers = useQuery({
    queryKey: ['teachers'],
    queryFn: () => api.get<Teacher[]>('/api/admin/teachers'),
  })

  const assign = useMutation({
    mutationFn: () =>
      api.post(`/api/admin/classes/${classId}/assign-course`, {
        courseId: Number(courseId),
        teacherId: Number(teacherId),
      }),
    onSuccess: () => {
      notify('Course assigned', 'success')
      setCourseId('')
      setTeacherId('')
      void queryClient.invalidateQueries({ queryKey: ['class-detail', classId] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    assign.mutate()
  }

  return (
    <>
      <Card title={`${group.courses.length} course${group.courses.length === 1 ? '' : 's'}`}>
        {group.courses.length === 0 ? (
          <EmptyState title="No courses assigned" hint="Assign one below." />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Course</th>
                  <th>Credits</th>
                  <th>Teacher</th>
                </tr>
              </thead>
              <tbody>
                {group.courses.map((assignment) => (
                  <tr key={assignment.id}>
                    <td>
                      <span className="mono">{assignment.courseCode}</span>
                      <div className="small muted">{assignment.courseName}</div>
                    </td>
                    <td className="mono">{assignment.credits ?? '—'}</td>
                    <td>
                      {assignment.teacherName}
                      <div className="small muted">{assignment.teacherDesignation ?? ''}</div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="Assign a course">
        <form onSubmit={submit} noValidate>
          <div className="grid grid-2">
            <Field label="Course" htmlFor="assign-course">
              <select
                id="assign-course"
                value={courseId}
                required
                onChange={(e) => setCourseId(e.target.value)}
              >
                <option value="">Select…</option>
                {(courses.data ?? []).map((course) => (
                  <option key={course.id} value={course.id}>
                    {course.code} — {course.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Teacher" htmlFor="assign-teacher">
              <select
                id="assign-teacher"
                value={teacherId}
                required
                onChange={(e) => setTeacherId(e.target.value)}
              >
                <option value="">Select…</option>
                {(teachers.data ?? []).map((teacher) => (
                  <option key={teacher.id} value={teacher.id}>
                    {teacher.fullName}
                    {teacher.designation ? ` (${teacher.designation})` : ''}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <button className="btn" type="submit" disabled={assign.isPending || !courseId || !teacherId}>
            {assign.isPending ? 'Assigning…' : 'Assign'}
          </button>
        </form>
      </Card>
    </>
  )
}

function Routine({ group }: { group: ClassDetail }) {
  return (
    <Card
      title="Weekly routine"
      actions={
        <Link className="btn btn-secondary btn-sm" to="/schedule">
          Edit the routine
        </Link>
      }
    >
      {group.routine.length === 0 ? (
        <EmptyState title="No routine set" hint="Add classes from the routine screen." />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Day</th>
                <th>Time</th>
                <th>Course</th>
                <th>Room</th>
                <th>Teacher</th>
              </tr>
            </thead>
            <tbody>
              {group.routine.map((item) => (
                <tr key={item.id}>
                  <td>{item.dayOfWeek}</td>
                  <td className="mono small">
                    {formatTime(item.startTime)}–{formatTime(item.endTime)}
                  </td>
                  <td>{item.courseName}</td>
                  <td>{item.roomNo ?? '—'}</td>
                  <td>{item.teacherName ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

function Promotions({ classId, group }: { classId: string; group: ClassDetail }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: ['class-detail', classId] })
    void queryClient.invalidateQueries({ queryKey: ['classes'] })
  }

  const promote = useMutation({
    mutationFn: () => api.post<{ message: string }>(`/api/admin/classes/${classId}/promote`),
    onSuccess: (result) => {
      notify(result.message, 'success')
      invalidate()
    },
    onError: (error) => reportError(error),
  })

  const rollback = useMutation({
    mutationFn: () =>
      api.post<{ message: string }>(`/api/admin/classes/${classId}/rollback-promotion`),
    onSuccess: (result) => {
      notify(result.message, 'success')
      invalidate()
    },
    onError: (error) => reportError(error),
  })

  return (
    <>
      <Card title="Move the class on a semester">
        <Alert kind="info">
          Promoting moves every student in {group.className} to the next semester. Undoing puts
          them back where they were, along with the course assignments at the time.
        </Alert>
        <div className="row">
          <button
            className="btn"
            disabled={promote.isPending}
            onClick={() => {
              if (window.confirm(`Promote every student in ${group.className}?`)) promote.mutate()
            }}
          >
            Promote a semester
          </button>
          <button
            className="btn btn-secondary"
            disabled={rollback.isPending}
            onClick={() => {
              if (window.confirm('Undo the most recent promotion?')) rollback.mutate()
            }}
          >
            Undo the last promotion
          </button>
        </div>
      </Card>

      <Card title="History">
        {group.promotions.length === 0 ? (
          <EmptyState title="Never promoted" />
        ) : (
          <div>
            {group.promotions.map((entry) => (
              <div className="timeline-item" key={entry.id}>
                <div className="timeline-time small">{formatDateTime(entry.timestamp)}</div>
                <div>
                  <div className="slot-course">
                    {entry.fromSemester} → {entry.toSemester}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </>
  )
}
