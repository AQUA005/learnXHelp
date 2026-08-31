import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { hasAtLeast, useCurrentUser } from '@/lib/session'
import { useToast } from '@/lib/toast'
import { DAY_ORDER, formatDateTime, formatTime, titleCase } from '@/lib/format'
import type { ClassTest, RoutineItem } from '@/lib/types'
import { Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'
import LiveRoutine from './LiveRoutine'

/**
 * The routine, and the class tests scheduled against it.
 *
 * The routine itself is the sheet the university publishes, read through
 * {@link LiveRoutine}. What a class keeps in LearnX -- the odd class the sheet
 * does not carry -- is merged into it rather than shown separately, and is
 * still edited here by whoever may edit it.
 */
export default function SchedulePage() {
  const user = useCurrentUser()
  const canEdit = hasAtLeast(user.role, 'CR')

  const routine = useQuery({
    queryKey: ['routine'],
    queryFn: () => api.get<RoutineItem[]>('/api/schedule/routine'),
  })
  const classTests = useQuery({
    queryKey: ['classTests'],
    queryFn: () => api.get<ClassTest[]>('/api/schedule/ct'),
  })

  return (
    <>
      <PageHeader
        title="Class routine"
        description="Read from the sheet your university publishes."
      />

      <LiveRoutine />

      {canEdit && <RoutineEditor />}

      {canEdit && (routine.data ?? []).length > 0 && (
        <Card title="Classes kept in LearnX">
          <p className="small muted">
            These are merged into the week above, marked as coming from your class list.
          </p>
          <div className="week-grid">
            {DAY_ORDER.map((day) => {
              const classes = (routine.data ?? [])
                .filter((item) => item.dayOfWeek?.toUpperCase() === day)
                .sort((a, b) => a.startTime.localeCompare(b.startTime))
              if (classes.length === 0) return null
              return (
                <div className="day-column" key={day}>
                  <h3>{titleCase(day)}</h3>
                  {classes.map((item) => (
                    <div className="slot" key={item.id}>
                      <div className="slot-course">{item.courseName}</div>
                      <div className="slot-time mono">
                        {formatTime(item.startTime)} – {formatTime(item.endTime)}
                      </div>
                      <div className="small muted">
                        {[item.roomNo, item.teacherName].filter(Boolean).join(' · ')}
                      </div>
                      <DeleteRoutineButton id={item.id} />
                    </div>
                  ))}
                </div>
              )
            })}
          </div>
        </Card>
      )}

      <Card title="Class tests">
        {canEdit && <ClassTestForm />}
        {classTests.isLoading ? (
          <Loading rows={3} />
        ) : (classTests.data ?? []).length === 0 ? (
          <EmptyState title="No class tests scheduled" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Course</th>
                  <th>When</th>
                  <th>Room</th>
                  <th>Topic</th>
                  {canEdit && <th />}
                </tr>
              </thead>
              <tbody>
                {(classTests.data ?? [])
                  .slice()
                  .sort((a, b) => a.dateTime.localeCompare(b.dateTime))
                  .map((test) => {
                    const past = new Date(test.dateTime).getTime() < Date.now()
                    return (
                      <tr key={test.id}>
                        <td>
                          {test.courseName} {past && <Badge>past</Badge>}
                        </td>
                        <td>{formatDateTime(test.dateTime)}</td>
                        <td>{test.roomNo ?? '—'}</td>
                        <td>{test.topic ?? '—'}</td>
                        {canEdit && (
                          <td>
                            <DeleteClassTestButton id={test.id} />
                          </td>
                        )}
                      </tr>
                    )
                  })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}

function RoutineEditor() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({
    courseName: '',
    dayOfWeek: 'MONDAY',
    startTime: '09:00',
    endTime: '10:00',
    teacherName: '',
    roomNo: '',
  })

  const create = useMutation({
    mutationFn: (payload: typeof form) =>
      api.post<RoutineItem>('/api/schedule/routine', {
        ...payload,
        // The server expects a time of day, not a duration.
        startTime: `${payload.startTime}:00`,
        endTime: `${payload.endTime}:00`,
      }),
    onSuccess: () => {
      notify('Class added to the routine', 'success')
      setForm((f) => ({ ...f, courseName: '', roomNo: '' }))
      void queryClient.invalidateQueries({ queryKey: ['routine'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate(form)
  }

  return (
    <Card
      title="Add a class"
      actions={
        <button className="btn btn-secondary btn-sm" onClick={() => setOpen((v) => !v)}>
          {open ? 'Hide' : 'Show'}
        </button>
      }
    >
      {open && (
        <form onSubmit={submit}>
          <div className="grid grid-3">
            <Field label="Course" htmlFor="rt-course">
              <input
                id="rt-course"
                required
                value={form.courseName}
                onChange={(e) => setForm({ ...form, courseName: e.target.value })}
              />
            </Field>
            <Field label="Day" htmlFor="rt-day">
              <select
                id="rt-day"
                value={form.dayOfWeek}
                onChange={(e) => setForm({ ...form, dayOfWeek: e.target.value })}
              >
                {DAY_ORDER.map((day) => (
                  <option key={day} value={day}>
                    {titleCase(day)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Room" htmlFor="rt-room">
              <input
                id="rt-room"
                value={form.roomNo}
                onChange={(e) => setForm({ ...form, roomNo: e.target.value })}
              />
            </Field>
            <Field label="Starts" htmlFor="rt-start">
              <input
                id="rt-start"
                type="time"
                required
                value={form.startTime}
                onChange={(e) => setForm({ ...form, startTime: e.target.value })}
              />
            </Field>
            <Field label="Ends" htmlFor="rt-end">
              <input
                id="rt-end"
                type="time"
                required
                value={form.endTime}
                onChange={(e) => setForm({ ...form, endTime: e.target.value })}
              />
            </Field>
            <Field label="Teacher" htmlFor="rt-teacher">
              <input
                id="rt-teacher"
                value={form.teacherName}
                onChange={(e) => setForm({ ...form, teacherName: e.target.value })}
              />
            </Field>
          </div>
          <button className="btn" type="submit" disabled={create.isPending}>
            {create.isPending ? 'Adding…' : 'Add class'}
          </button>
        </form>
      )}
    </Card>
  )
}

function ClassTestForm() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [form, setForm] = useState({
    courseName: '',
    dateTime: '',
    durationMinutes: 60,
    roomNo: '',
    topic: '',
  })

  const create = useMutation({
    mutationFn: (payload: typeof form) => api.post<ClassTest>('/api/schedule/ct', payload),
    onSuccess: () => {
      notify('Class test scheduled', 'success')
      setForm({ courseName: '', dateTime: '', durationMinutes: 60, roomNo: '', topic: '' })
      void queryClient.invalidateQueries({ queryKey: ['classTests'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate(form)
  }

  return (
    <form onSubmit={submit} style={{ marginBottom: '1rem' }}>
      <div className="grid grid-3">
        <Field label="Course" htmlFor="ct-course">
          <input
            id="ct-course"
            required
            value={form.courseName}
            onChange={(e) => setForm({ ...form, courseName: e.target.value })}
          />
        </Field>
        <Field label="Date and time" htmlFor="ct-when">
          <input
            id="ct-when"
            type="datetime-local"
            required
            value={form.dateTime}
            onChange={(e) => setForm({ ...form, dateTime: e.target.value })}
          />
        </Field>
        <Field label="Duration (minutes)" htmlFor="ct-duration">
          <input
            id="ct-duration"
            type="number"
            min={5}
            value={form.durationMinutes}
            onChange={(e) => setForm({ ...form, durationMinutes: Number(e.target.value) })}
          />
        </Field>
        <Field label="Room" htmlFor="ct-room">
          <input
            id="ct-room"
            value={form.roomNo}
            onChange={(e) => setForm({ ...form, roomNo: e.target.value })}
          />
        </Field>
        <Field label="Topic" htmlFor="ct-topic">
          <input
            id="ct-topic"
            value={form.topic}
            onChange={(e) => setForm({ ...form, topic: e.target.value })}
          />
        </Field>
      </div>
      <button className="btn" type="submit" disabled={create.isPending}>
        {create.isPending ? 'Scheduling…' : 'Schedule class test'}
      </button>
    </form>
  )
}

function DeleteRoutineButton({ id }: { id: number }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const remove = useMutation({
    mutationFn: () => api.del(`/api/schedule/routine/${id}`),
    onSuccess: () => {
      notify('Class removed', 'success')
      void queryClient.invalidateQueries({ queryKey: ['routine'] })
    },
    onError: (error) => reportError(error),
  })

  return (
    <button
      className="btn btn-secondary btn-sm"
      style={{ marginTop: '0.4rem' }}
      onClick={() => remove.mutate()}
      disabled={remove.isPending}
    >
      Remove
    </button>
  )
}

function DeleteClassTestButton({ id }: { id: number }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const remove = useMutation({
    mutationFn: () => api.del(`/api/schedule/ct/${id}`),
    onSuccess: () => {
      notify('Class test cancelled', 'success')
      void queryClient.invalidateQueries({ queryKey: ['classTests'] })
    },
    onError: (error) => reportError(error),
  })

  return (
    <button className="btn btn-secondary btn-sm" onClick={() => remove.mutate()} disabled={remove.isPending}>
      Cancel
    </button>
  )
}
