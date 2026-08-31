import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { titleCase } from '@/lib/format'
import { Badge, Field } from '@/components/ui'
import { dayKeyOf, ymd } from './routineData'
import type { LiveDay, RoutineOverride } from './routineData'

/**
 * What a class representative announces between publications.
 *
 * Cancelling picks a class out of the sheet for one date rather than typing it
 * again, so the thing being cancelled and the thing on the screen are the same
 * thing. Everything posted here is seen by the whole class -- a cancelled class
 * is news, not a private note -- and can be taken back if it was wrong.
 */
export default function ManageChanges({
  days,
  overrides,
  onClose,
  onChanged,
}: {
  days: LiveDay[]
  overrides: RoutineOverride[]
  onClose: () => void
  onChanged: () => void
}) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const [date, setDate] = useState(ymd(new Date()))
  const [course, setCourse] = useState('')
  const [room, setRoom] = useState('')
  const [teacher, setTeacher] = useState('')
  const [start, setStart] = useState('09:00')
  const [end, setEnd] = useState('10:00')
  const [note, setNote] = useState('')

  function refreshed() {
    void queryClient.invalidateQueries({ queryKey: ['live-routine'] })
    onChanged()
  }

  const post = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.post('/api/routine/overrides', body),
    onSuccess: () => {
      notify('Posted to your class', 'success')
      refreshed()
    },
    onError: (error) => reportError(error),
  })

  const remove = useMutation({
    mutationFn: (id: number) => api.del(`/api/routine/overrides/${id}`),
    onSuccess: () => {
      notify('Change withdrawn', 'success')
      refreshed()
    },
    onError: (error) => reportError(error),
  })

  const dayKey = dayKeyOf(new Date(`${date}T00:00:00`))
  const scheduled = days.find((day) => day.day === dayKey)?.classes ?? []
  const forDate = overrides.filter((override) => override.date === date)

  function cancelledId(key: string): number | undefined {
    return forDate.find(
      (override) => override.kind === 'CANCELLED' && override.targetKey === key,
    )?.id
  }

  function addClass(event: FormEvent) {
    event.preventDefault()
    post.mutate({ date, kind: 'ADDED', course, room, teacher, start, end, note })
    setCourse('')
    setRoom('')
    setNote('')
  }

  return (
    <div className="modal-back" onClick={(event) => event.target === event.currentTarget && onClose()}>
      <div className="modal card" role="dialog" aria-label="Post a change">
        <header className="card-head">
          <h2>Post a change</h2>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>
            Close
          </button>
        </header>

        <Field label="Date" htmlFor="mc-date">
          <input
            id="mc-date"
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </Field>

        <h3 className="routine-section-title">
          Cancel a class on {titleCase(dayKey)}
        </h3>
        {scheduled.length === 0 ? (
          <p className="small muted">Nothing is scheduled on that date.</p>
        ) : (
          scheduled.map((item) => {
            const id = cancelledId(item.key)
            return (
              <div className="routine-manage-row" key={item.key}>
                <span className="mono small">{item.timeText}</span>
                <span className={id ? 'routine-manage-course cancelled' : 'routine-manage-course'}>
                  {item.course}
                </span>
                {id ? (
                  <button
                    className="btn btn-secondary btn-sm"
                    disabled={remove.isPending}
                    onClick={() => remove.mutate(id)}
                  >
                    Restore
                  </button>
                ) : (
                  <button
                    className="btn btn-danger btn-sm"
                    disabled={post.isPending}
                    onClick={() =>
                      post.mutate({
                        date,
                        kind: 'CANCELLED',
                        targetKey: item.key,
                        course: item.course,
                      })
                    }
                  >
                    Cancel
                  </button>
                )}
              </div>
            )
          })
        )}

        <h3 className="routine-section-title">Add an extra class</h3>
        <form onSubmit={addClass}>
          <div className="grid grid-2">
            <Field label="Course" htmlFor="mc-course">
              <input
                id="mc-course"
                required
                placeholder="e.g. CSE 3101"
                value={course}
                onChange={(event) => setCourse(event.target.value)}
              />
            </Field>
            <Field label="Room" htmlFor="mc-room">
              <input id="mc-room" value={room} onChange={(event) => setRoom(event.target.value)} />
            </Field>
            <Field label="Starts" htmlFor="mc-start">
              <input
                id="mc-start"
                type="time"
                required
                value={start}
                onChange={(event) => setStart(event.target.value)}
              />
            </Field>
            <Field label="Ends" htmlFor="mc-end">
              <input
                id="mc-end"
                type="time"
                required
                value={end}
                onChange={(event) => setEnd(event.target.value)}
              />
            </Field>
            <Field label="Teacher" htmlFor="mc-teacher">
              <input
                id="mc-teacher"
                value={teacher}
                onChange={(event) => setTeacher(event.target.value)}
              />
            </Field>
            <Field label="Note" htmlFor="mc-note">
              <input
                id="mc-note"
                placeholder="Optional — e.g. makeup class"
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
            </Field>
          </div>
          <button className="btn" type="submit" disabled={post.isPending}>
            {post.isPending ? 'Posting…' : 'Post to my class'}
          </button>
        </form>

        <h3 className="routine-section-title">Posted for this date</h3>
        {forDate.length === 0 ? (
          <p className="small muted">Nothing posted for {date}.</p>
        ) : (
          forDate.map((override) => (
            <div className="routine-manage-row" key={override.id}>
              <span className="mono small">{override.timeText || '—'}</span>
              <span className="routine-manage-course">
                {override.course ?? override.targetKey}{' '}
                <Badge kind={override.kind === 'CANCELLED' ? 'danger' : 'accent'}>
                  {override.kind === 'CANCELLED' ? 'Cancelled' : 'Added'}
                </Badge>
              </span>
              <button
                className="btn btn-secondary btn-sm"
                disabled={remove.isPending}
                onClick={() => remove.mutate(override.id)}
              >
                Withdraw
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
