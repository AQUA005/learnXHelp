import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { todayName } from '@/lib/format'
import type { Announcement, ClassTest, PerformanceStat, RoutineItem } from '@/lib/types'

/**
 * The data behind the dashboards, and the arithmetic they share.
 *
 * The query keys are exactly those used by the routine, announcements and
 * results screens, so opening a dashboard warms their cache instead of asking
 * for the same rows a second time.
 */

export function useRoutine() {
  return useQuery({
    queryKey: ['routine'],
    queryFn: () => api.get<RoutineItem[]>('/api/schedule/routine'),
  })
}

export function useClassTests() {
  return useQuery({
    queryKey: ['classTests'],
    queryFn: () => api.get<ClassTest[]>('/api/schedule/ct'),
  })
}

export function useAnnouncements() {
  return useQuery({
    queryKey: ['announcements'],
    queryFn: () => api.get<Announcement[]>('/api/announcements'),
  })
}

export function usePerformance() {
  return useQuery({
    queryKey: ['performance'],
    queryFn: () => api.get<PerformanceStat[]>('/api/dashboard/performance'),
  })
}

// --- Shared arithmetic ---

export function todaysClassesFrom(routine: RoutineItem[]): RoutineItem[] {
  const today = todayName()
  return routine
    .filter((item) => item.dayOfWeek?.toUpperCase() === today)
    .sort((a, b) => a.startTime.localeCompare(b.startTime))
}

export function upcomingTestsFrom(tests: ClassTest[], limit = 4): ClassTest[] {
  return tests
    .filter((test) => new Date(test.dateTime).getTime() > Date.now())
    .sort((a, b) => a.dateTime.localeCompare(b.dateTime))
    .slice(0, limit)
}

/** Mean percentage across published results, rounded. */
export function averagePercentage(stats: PerformanceStat[]): number | null {
  const scored = stats.filter((stat) => stat.maxMarks > 0)
  if (scored.length === 0) return null
  const total = scored.reduce((sum, stat) => sum + (stat.marksObtained / stat.maxMarks) * 100, 0)
  return Math.round(total / scored.length)
}

export function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] || 'there'
}

export function partOfDay(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'morning'
  if (hour < 17) return 'afternoon'
  return 'evening'
}

export function summaryLine(classes: number, tests: number): string {
  if (classes === 0 && tests === 0) return 'Nothing scheduled at the moment.'
  const parts: string[] = []
  if (classes > 0) parts.push(`${classes} class${classes === 1 ? '' : 'es'} today`)
  if (tests > 0) parts.push(`${tests} class test${tests === 1 ? '' : 's'} coming up`)
  return `${parts.join(', ')}.`
}
