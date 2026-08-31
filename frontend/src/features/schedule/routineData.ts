import type { RoutineItem } from '@/lib/types'

/**
 * The published routine, and the arithmetic the screen does on it.
 *
 * The server hands over the sheet as weekday templates plus the dated changes
 * a class representative has posted. Turning that into "what is happening on
 * Tuesday the 14th" happens here, on the client, because the answer depends on
 * the reader's own clock.
 */

export type LiveClass = {
  /** How a cancellation names this class: time and course, from the sheet. */
  key: string
  timeText: string
  startMinute: number
  endMinute: number
  periods: number
  course: string
  room: string
  teacherCode: string
  teacherName: string
}

export type LiveDay = { day: string; classes: LiveClass[] }

export type RoutineOverride = {
  id: number
  date: string
  kind: 'ADDED' | 'CANCELLED'
  targetKey: string | null
  course: string | null
  room: string | null
  teacher: string | null
  startMinute: number | null
  endMinute: number | null
  timeText: string
  note: string | null
  createdBy: string
  createdAt: string
  studentClassId: number | null
  className: string | null
}

export type LiveRoutineResponse = {
  configured: boolean
  section: string
  semester: string
  session: string
  sections: string[]
  days: LiveDay[]
  overrides: RoutineOverride[]
  sheetUrl: string | null
  stale: boolean
  fetchedAt: string | null
  daysLoaded: number
  daysRequested: number
  message: string | null
}

export type RoutineSource = {
  id: number
  department: string
  sheetId: string
  sheetUrl: string
  dayGids: string
  teacherGid: string | null
  blockHints: string | null
  updatedBy: string | null
  updatedAt: string | null
}

/** Where a class on the screen came from. */
export type Origin = 'SHEET' | 'ADDED' | 'CLASS_LIST'

export type MergedClass = LiveClass & {
  origin: Origin
  cancelled: boolean
  overrideId?: number
  note?: string | null
  postedBy?: string
}

export const DAY_KEYS = [
  'SUNDAY',
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
] as const

/** Spacing and case in a sheet are not meaningful; this is the comparison key. */
export function norm(value: string | null | undefined): string {
  return (value ?? '').replace(/\s+/g, '').toLowerCase()
}

export function dayKeyOf(date: Date): string {
  return DAY_KEYS[date.getDay()]
}

export function ymd(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** The concrete date of each weekday in the week containing `today`. */
export function weekDates(today: Date): Record<string, Date> {
  const start = new Date(today)
  start.setHours(0, 0, 0, 0)
  start.setDate(today.getDate() - today.getDay())

  const dates: Record<string, Date> = {}
  for (let i = 0; i < 7; i += 1) {
    const date = new Date(start)
    date.setDate(start.getDate() + i)
    dates[DAY_KEYS[date.getDay()]] = date
  }
  return dates
}

/** "09:30:00" or "09:30" as minutes from midnight. */
export function minutesOfTime(value: string): number {
  const match = /(\d{1,2}):(\d{2})/.exec(value)
  return match ? Number(match[1]) * 60 + Number(match[2]) : 0
}

export function formatMinutes(minutes: number): string {
  const hour = Math.floor(minutes / 60) % 12 || 12
  return `${hour}:${`${minutes % 60}`.padStart(2, '0')}`
}

export function timeTextOf(startMinute: number, endMinute: number): string {
  return `${formatMinutes(startMinute)}–${formatMinutes(endMinute)}`
}

export function humanGap(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return `${hours}h${rest ? ` ${rest}m` : ''}`
}

export function keyOf(timeText: string, course: string): string {
  return `${timeText}|${norm(course)}`
}

/**
 * What is actually happening on one date.
 *
 * The sheet's weekday template, plus the classes this class keeps in LearnX
 * itself, plus anything the class representative added for the date, minus
 * anything they cancelled. Cancellations match on the class's key rather than
 * on an id, because the sheet has no ids and a row may move between
 * publications.
 */
export function mergeDay(
  dayKey: string,
  dateText: string,
  days: LiveDay[],
  overrides: RoutineOverride[],
  classList: RoutineItem[],
): MergedClass[] {
  const fromSheet: MergedClass[] = (days.find((day) => day.day === dayKey)?.classes ?? []).map(
    (item) => ({ ...item, origin: 'SHEET', cancelled: false }),
  )

  const fromClassList: MergedClass[] = classList
    .filter((item) => item.dayOfWeek?.toUpperCase() === dayKey)
    .map((item) => {
      const startMinute = minutesOfTime(item.startTime)
      const endMinute = minutesOfTime(item.endTime)
      const timeText = timeTextOf(startMinute, endMinute)
      return {
        key: keyOf(timeText, item.courseName),
        timeText,
        startMinute,
        endMinute,
        periods: 1,
        course: item.courseName,
        room: item.roomNo ?? '',
        teacherCode: '',
        teacherName: item.teacherName ?? '',
        origin: 'CLASS_LIST' as const,
        cancelled: false,
      }
    })
    // A class the sheet already carries is not shown twice.
    .filter((item) => !fromSheet.some((sheet) => sheet.key === item.key))

  const added: MergedClass[] = overrides
    .filter((override) => override.kind === 'ADDED' && override.date === dateText)
    .map((override) => ({
      key: `added|${override.id}`,
      timeText: override.timeText,
      startMinute: override.startMinute ?? 0,
      endMinute: override.endMinute ?? 0,
      periods: 1,
      course: override.course ?? '',
      room: override.room ?? '',
      teacherCode: '',
      teacherName: override.teacher ?? '',
      origin: 'ADDED',
      cancelled: false,
      overrideId: override.id,
      note: override.note,
      postedBy: override.createdBy,
    }))

  const cancelled = overrides.filter(
    (override) => override.kind === 'CANCELLED' && override.date === dateText,
  )

  return [...fromSheet, ...fromClassList, ...added]
    .map((item) => {
      const match = cancelled.find((override) => norm(override.targetKey) === norm(item.key))
      return match
        ? { ...item, cancelled: true, overrideId: match.id, note: match.note, postedBy: match.createdBy }
        : item
    })
    .sort((a, b) => a.startMinute - b.startMinute)
}

export function activeOf(classes: MergedClass[]): MergedClass[] {
  return classes.filter((item) => !item.cancelled)
}

/** The next day that has anything on it, for the "nothing today" card. */
export function nextTeachingDay(
  today: Date,
  days: LiveDay[],
  overrides: RoutineOverride[],
  classList: RoutineItem[],
): { day: string; date: Date; count: number } | null {
  for (let step = 1; step <= 7; step += 1) {
    const date = new Date(today)
    date.setDate(today.getDate() + step)
    const count = activeOf(
      mergeDay(dayKeyOf(date), ymd(date), days, overrides, classList),
    ).length
    if (count > 0) return { day: dayKeyOf(date), date, count }
  }
  return null
}

// --- What this browser remembers ------------------------------------------

/**
 * The section being read, and a sheet of one's own.
 *
 * Kept per browser rather than on the account: the section is a preference
 * about a shared timetable, and the sheet override exists precisely for the
 * case where nothing has been configured centrally yet.
 */
export type RoutinePrefs = {
  section?: string
  sheet?: string
  dayGids?: string
  teacherGid?: string
}

const PREFS_KEY = 'learnx.routine'

export function readPrefs(): RoutinePrefs {
  try {
    const raw = localStorage.getItem(PREFS_KEY)
    return raw ? (JSON.parse(raw) as RoutinePrefs) : {}
  } catch {
    return {}
  }
}

export function writePrefs(prefs: RoutinePrefs): void {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(prefs))
  } catch {
    // A browser with storage blocked still works; it just forgets the section.
  }
}

/** The query string for `/api/routine/live`, from the stored preferences. */
export function liveQuery(prefs: RoutinePrefs, refresh = false): string {
  const params = new URLSearchParams()
  if (prefs.section) params.set('section', prefs.section)
  if (prefs.sheet) {
    params.set('sheet', prefs.sheet)
    if (prefs.dayGids) params.set('dayGids', prefs.dayGids)
    if (prefs.teacherGid) params.set('teacherGid', prefs.teacherGid)
  }
  if (refresh) params.set('refresh', 'true')
  const query = params.toString()
  return query ? `?${query}` : ''
}
