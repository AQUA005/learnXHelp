import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { hasAtLeast, useCurrentUser } from '@/lib/session'
import { useToast } from '@/lib/toast'
import { titleCase } from '@/lib/format'
import type { RoutineItem } from '@/lib/types'
import { Badge, Loading } from '@/components/ui'
import { Icon } from '@/components/icons'
import {
  activeOf,
  dayKeyOf,
  humanGap,
  liveQuery,
  mergeDay,
  nextTeachingDay,
  readPrefs,
  weekDates,
  writePrefs,
  ymd,
} from './routineData'
import type { LiveRoutineResponse, MergedClass, RoutinePrefs } from './routineData'
import ManageChanges from './ManageChanges'
import RoutineSetup from './RoutineSetup'

/**
 * The routine, read from the sheet the university publishes.
 *
 * Today leads, because that is the question being asked: what is on now, what
 * is next, and how much of the current class is left. The rest of the week sits
 * underneath, collapsed, for the times the question is a different one.
 */
export default function LiveRoutine() {
  const user = useCurrentUser()
  const canPost = hasAtLeast(user.role, 'CR')
  const queryClient = useQueryClient()
  const { reportError } = useToast()

  const [prefs, setPrefs] = useState<RoutinePrefs>(readPrefs)
  const [setupOpen, setSetupOpen] = useState(false)
  const [manageOpen, setManageOpen] = useState(false)

  // Re-renders the "now" card as the clock moves, without refetching anything.
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30_000)
    return () => clearInterval(timer)
  }, [])

  const routine = useQuery({
    queryKey: ['live-routine', prefs],
    queryFn: () => api.get<LiveRoutineResponse>(`/api/routine/live${liveQuery(prefs)}`),
    staleTime: 60_000,
  })

  // The classes this class keeps in LearnX itself, merged with the sheet's.
  const classList = useQuery({
    queryKey: ['routine'],
    queryFn: () => api.get<RoutineItem[]>('/api/schedule/routine'),
  })

  const refresh = useMutation({
    mutationFn: () =>
      api.get<LiveRoutineResponse>(`/api/routine/live${liveQuery(prefs, true)}`),
    onSuccess: (fresh) => queryClient.setQueryData(['live-routine', prefs], fresh),
    onError: (error) => reportError(error),
  })

  const data = routine.data
  // Held steady between renders: the merge below runs on every tick of the
  // clock, and a fresh empty array each time would redo all of it for nothing.
  const days = useMemo(() => data?.days ?? [], [data])
  const overrides = useMemo(() => data?.overrides ?? [], [data])
  const items = useMemo(() => classList.data ?? [], [classList.data])

  /** The sheet was read, but it had nothing for the section being asked for. */
  const sheetIsEmpty = days.every((day) => day.classes.length === 0)

  const dates = useMemo(() => weekDates(now), [now])
  const todayKey = dayKeyOf(now)
  const todayText = ymd(now)
  const today = useMemo(
    () => mergeDay(todayKey, todayText, days, overrides, items),
    [todayKey, todayText, days, overrides, items],
  )

  function savePrefs(next: RoutinePrefs) {
    setPrefs(next)
    writePrefs(next)
  }

  if (routine.isLoading) {
    return (
      <section className="card routine-hero">
        <Loading rows={4} label="Reading the routine" />
      </section>
    )
  }

  return (
    <>
      <div className="routine-bar">
        <StatusLine
          response={data}
          loading={routine.isFetching || refresh.isPending}
          error={routine.isError}
        />
        <div className="row">
          {canPost && (
            <button className="btn btn-secondary btn-sm" onClick={() => setManageOpen(true)}>
              Post a change
            </button>
          )}
          <button className="btn btn-secondary btn-sm" onClick={() => setSetupOpen(true)}>
            {data?.section ? `Section ${data.section.toUpperCase()}` : 'Choose section'}
          </button>
          <button
            className="btn btn-sm"
            onClick={() => refresh.mutate()}
            disabled={refresh.isPending}
          >
            {refresh.isPending ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      {data && !data.configured && (
        <div className="alert alert-info">
          {data.message ?? 'No routine sheet has been set for your department yet.'}{' '}
          {hasAtLeast(user.role, 'ADMIN')
            ? 'Set one in Administration → Routine sheets.'
            : 'You can point this screen at one yourself from the section button.'}
        </div>
      )}

      {/* The sheet was read, but the section guessed from this account is not
          one of the sections in it. Better to say so than to show an empty
          week that looks like a holiday. */}
      {data?.configured && data.sections.length > 0 && sheetIsEmpty && (
        <div className="alert alert-info">
          This sheet lists {data.sections.length} sections and none of them is{' '}
          <b>{data.section || 'the one on your account'}</b>.{' '}
          <button className="btn btn-sm" onClick={() => setSetupOpen(true)}>
            Choose your section
          </button>
        </div>
      )}

      <Today now={now} classes={today} days={days} overrides={overrides} items={items} />

      <h2 className="routine-week-title">The rest of the week</h2>
      <div className="routine-week">
        {Object.keys(dates)
          .sort((a, b) => WEEK_ORDER.indexOf(a) - WEEK_ORDER.indexOf(b))
          .map((key) => (
            <DayCard
              key={key}
              dayKey={key}
              date={dates[key]}
              isToday={key === todayKey}
              classes={mergeDay(key, ymd(dates[key]), days, overrides, items)}
            />
          ))}
      </div>

      {data?.sheetUrl && (
        <p className="small muted routine-source-link">
          <a href={data.sheetUrl} target="_blank" rel="noopener noreferrer">
            Open the source sheet ↗
          </a>
          {data.session ? ` · ${data.session}` : ''}
          {data.semester ? ` · ${data.semester} semester` : ''}
        </p>
      )}

      {setupOpen && (
        <RoutineSetup
          prefs={prefs}
          sections={data?.sections ?? []}
          onSave={(next) => {
            savePrefs(next)
            setSetupOpen(false)
          }}
          onClose={() => setSetupOpen(false)}
        />
      )}

      {manageOpen && (
        <ManageChanges
          days={days}
          overrides={overrides}
          onClose={() => setManageOpen(false)}
          onChanged={() => void queryClient.invalidateQueries({ queryKey: ['live-routine'] })}
        />
      )}
    </>
  )
}

const WEEK_ORDER = [
  'SUNDAY',
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
]

/** Where the data came from and how much to trust it. */
function StatusLine({
  response,
  loading,
  error,
}: {
  response?: LiveRoutineResponse
  loading: boolean
  error: boolean
}) {
  if (error) {
    return (
      <span className="routine-status err">
        <span className="dot" /> The routine could not be read.
      </span>
    )
  }
  if (loading) {
    return (
      <span className="routine-status">
        <span className="dot" /> Reading the sheet…
      </span>
    )
  }
  if (!response?.configured) {
    return (
      <span className="routine-status stale">
        <span className="dot" /> No sheet configured
      </span>
    )
  }
  if (response.stale) {
    return (
      <span className="routine-status stale">
        <span className="dot" /> Saved copy — the sheet could not be reached
      </span>
    )
  }

  const at = response.fetchedAt
    ? new Date(response.fetchedAt).toLocaleTimeString(undefined, {
        hour: 'numeric',
        minute: '2-digit',
      })
    : null
  const partial =
    response.daysLoaded < response.daysRequested
      ? ` · ${response.daysLoaded}/${response.daysRequested} days`
      : ''

  return (
    <span className="routine-status">
      <span className="dot" /> Live{at ? ` · updated ${at}` : ''}
      {partial}
    </span>
  )
}

/** Today: what is on now, what is next, and what is left of it. */
function Today({
  now,
  classes,
  days,
  overrides,
  items,
}: {
  now: Date
  classes: MergedClass[]
  days: LiveRoutineResponse['days']
  overrides: LiveRoutineResponse['overrides']
  items: RoutineItem[]
}) {
  const nowMinutes = now.getHours() * 60 + now.getMinutes()
  const active = activeOf(classes)

  const ongoing = active.find(
    (item) => nowMinutes >= item.startMinute && nowMinutes < item.endMinute,
  )
  const next = active.find((item) => item.startMinute > nowMinutes)

  const heading = now.toLocaleDateString(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'short',
  })

  if (active.length === 0) {
    const upcoming = nextTeachingDay(now, days, overrides, items)
    return (
      <section className="card routine-hero">
        <header className="routine-hero-head">
          <h2>{titleCase(dayKeyOf(now))}</h2>
          <span className="small muted">{heading}</span>
        </header>
        <p className="routine-banner">
          No classes today.
          {upcoming
            ? ` Next: ${titleCase(upcoming.day)} — ${upcoming.count} class${upcoming.count === 1 ? '' : 'es'}.`
            : ''}
        </p>
      </section>
    )
  }

  return (
    <section className="card routine-hero">
      <header className="routine-hero-head">
        <h2>{titleCase(dayKeyOf(now))}</h2>
        <span className="small muted">{heading}</span>
      </header>

      {!ongoing && !next && (
        <p className="routine-banner">
          All {active.length} class{active.length === 1 ? '' : 'es'} done for today.
        </p>
      )}

      {classes.map((item) => (
        <div key={item.key}>
          {item === ongoing && (
            <span className="routine-tag">Now · ends {item.timeText.split('–')[1]}</span>
          )}
          {item === next && (
            <span className="routine-tag">
              Next · in {humanGap(item.startMinute - nowMinutes)}
            </span>
          )}
          <ClassRow item={item} live={item === ongoing} />
          {item === ongoing && <Progress item={item} nowMinutes={nowMinutes} />}
        </div>
      ))}
    </section>
  )
}

/** How far through the current class we are, and how long is left. */
function Progress({ item, nowMinutes }: { item: MergedClass; nowMinutes: number }) {
  const total = item.endMinute - item.startMinute
  const done = total > 0
    ? Math.max(0, Math.min(100, Math.round(((nowMinutes - item.startMinute) / total) * 100)))
    : 0
  const left = Math.max(0, item.endMinute - nowMinutes)

  return (
    <div className="routine-progress">
      <div className="routine-progress-track">
        <i style={{ width: `${done}%` }} />
      </div>
      <div className="routine-progress-meta small">
        <span>{done}% done</span>
        <span>
          <b>{humanGap(left)}</b> left
        </span>
      </div>
    </div>
  )
}

function ClassRow({ item, live = false }: { item: MergedClass; live?: boolean }) {
  const classes = ['routine-class']
  if (live) classes.push('now')
  if (item.cancelled) classes.push('cancelled')

  return (
    <div className={classes.join(' ')}>
      <div className="routine-time mono">{item.timeText}</div>
      <div className="routine-body">
        <div className="routine-course">
          {item.course}
          {item.periods > 1 && <Badge kind="accent">{item.periods} periods</Badge>}
          {item.cancelled && <Badge kind="danger">Cancelled</Badge>}
          {!item.cancelled && item.origin === 'ADDED' && <Badge kind="accent">Added</Badge>}
          {!item.cancelled && item.origin === 'CLASS_LIST' && <Badge>Class list</Badge>}
        </div>
        <div className="routine-meta small">
          {item.room && <span className="routine-room">Room {item.room}</span>}
          {item.teacherName && (
            <span>
              <b>{item.teacherName}</b>
              {item.teacherCode && item.teacherCode !== item.teacherName && (
                <span className="muted"> ({item.teacherCode})</span>
              )}
            </span>
          )}
          {item.note && <span className="muted">{item.note}</span>}
          {item.postedBy && (
            <span className="muted">
              · {item.cancelled ? 'cancelled' : 'added'} by {item.postedBy}
            </span>
          )}
        </div>
      </div>
    </div>
  )
}

/** One day of the week, collapsed until asked for. */
function DayCard({
  dayKey,
  date,
  isToday,
  classes,
}: {
  dayKey: string
  date: Date
  isToday: boolean
  classes: MergedClass[]
}) {
  const [open, setOpen] = useState(false)
  const active = activeOf(classes).length

  return (
    <div className={isToday ? 'card routine-day today' : 'card routine-day'}>
      <button
        type="button"
        className="routine-day-head"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <span className="routine-day-name">
          {titleCase(dayKey)}
          {isToday && <Badge kind="accent">Today</Badge>}
        </span>
        <span className="routine-day-meta small muted">
          <span>{date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}</span>
          <span>{active ? `${active} class${active === 1 ? '' : 'es'}` : '—'}</span>
          <span className={open ? 'routine-chevron open' : 'routine-chevron'} aria-hidden="true">
            <Icon name="chevron" />
          </span>
        </span>
      </button>

      {open && (
        <div className="routine-day-body">
          {classes.length === 0 ? (
            <p className="small muted">No classes.</p>
          ) : (
            classes.map((item) => <ClassRow key={item.key} item={item} />)
          )}
        </div>
      )}
    </div>
  )
}
