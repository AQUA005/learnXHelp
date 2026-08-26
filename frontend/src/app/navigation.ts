import type { IconName } from '@/components/icons'
import type { Role } from '@/lib/types'
import { ADMIN_DEFAULT_TAB, VIEW_PARAM, adminTabPath } from '@/features/admin/tabs'

/**
 * What each role sees in the sidebar.
 *
 * Every role gets its own list rather than one list filtered by rank, so a
 * teacher is not shown a student's screens with a few extra entries appended:
 * the wording, the order and the grouping all follow the job that role does.
 * This decides what is offered, never what is allowed — the route guards and
 * the server decide that.
 */

export type NavItem = {
  /** A router path, optionally carrying the view a screen should open on. */
  to: string
  label: string
  icon: IconName
}

export type NavSection = {
  title: string
  items: NavItem[]
}

const PROFILE: NavSection = {
  title: 'Account',
  items: [{ to: '/profile', label: 'Profile', icon: 'user' }],
}

const ADMINISTRATION: NavSection = {
  title: 'Administration',
  items: [
    { to: adminTabPath('approvals'), label: 'Account approvals', icon: 'approvals' },
    { to: adminTabPath('people'), label: 'People', icon: 'people' },
    { to: adminTabPath('classes'), label: 'Classes', icon: 'classes' },
    { to: adminTabPath('metadata'), label: 'Dropdown options', icon: 'options' },
    { to: adminTabPath('audit'), label: 'Change history', icon: 'history' },
  ],
}

const NAVIGATION: Record<Role, NavSection[]> = {
  STUDENT: [
    {
      title: 'Your day',
      items: [
        { to: '/', label: 'Home', icon: 'home' },
        { to: '/schedule', label: 'Class routine', icon: 'calendar' },
        { to: '/announcements', label: 'Announcements', icon: 'megaphone' },
      ],
    },
    {
      title: 'Study',
      items: [
        { to: '/notes', label: 'Notes library', icon: 'folder' },
        { to: '/exams', label: 'Online exams', icon: 'exam' },
        { to: '/performance', label: 'My results', icon: 'chart' },
      ],
    },
    PROFILE,
  ],

  // A class representative reads everything a student does, and also keeps the
  // routine and the announcements for their class up to date.
  CR: [
    {
      title: 'Your class',
      items: [
        { to: '/', label: 'Home', icon: 'home' },
        { to: '/schedule', label: 'Routine & test slots', icon: 'calendar' },
        { to: '/announcements', label: 'Announcements', icon: 'megaphone' },
      ],
    },
    {
      title: 'Study',
      items: [
        { to: '/notes', label: 'Notes library', icon: 'folder' },
        { to: '/exams', label: 'Online exams', icon: 'exam' },
        { to: '/performance', label: 'My results', icon: 'chart' },
      ],
    },
    PROFILE,
  ],

  TEACHER: [
    {
      title: 'Teaching',
      items: [
        { to: '/', label: 'Home', icon: 'home' },
        { to: '/schedule', label: 'Routine & class tests', icon: 'calendar' },
        { to: '/announcements', label: 'Announcements', icon: 'megaphone' },
      ],
    },
    {
      title: 'Classwork',
      items: [
        { to: '/exams', label: 'Exams', icon: 'exam' },
        { to: '/gradebook', label: 'Gradebook', icon: 'gradebook' },
        { to: '/moderation', label: 'Note approvals', icon: 'shield' },
        { to: '/notes', label: 'Notes library', icon: 'folder' },
      ],
    },
    PROFILE,
  ],

  ADMIN: [
    {
      title: 'Overview',
      items: [
        { to: '/', label: 'Home', icon: 'home' },
        { to: '/schedule', label: 'Master routine', icon: 'calendar' },
        { to: '/announcements', label: 'Global announcements', icon: 'megaphone' },
      ],
    },
    ADMINISTRATION,
    PROFILE,
  ],

  SYSTEM_ADMIN: [
    {
      title: 'Overview',
      items: [
        { to: '/', label: 'Home', icon: 'home' },
        { to: '/schedule', label: 'Master routine', icon: 'calendar' },
        { to: '/announcements', label: 'Global announcements', icon: 'megaphone' },
      ],
    },
    ADMINISTRATION,
    {
      title: 'Academic oversight',
      items: [
        { to: '/gradebook', label: 'Gradebook', icon: 'gradebook' },
        { to: '/moderation', label: 'Note approvals', icon: 'shield' },
        { to: '/notes', label: 'Notes library', icon: 'folder' },
      ],
    },
    PROFILE,
  ],
}

export function navigationFor(role: Role): NavSection[] {
  return NAVIGATION[role]
}

/**
 * Whether an entry is the one being looked at.
 *
 * Paths are compared whole, and an entry that names a view — the admin
 * screens do — also has to match the view the address is on, so only one of
 * them lights up at a time.
 */
export function isNavItemActive(item: NavItem, pathname: string, search: string): boolean {
  const [path, query] = item.to.split('?')
  if (pathname !== path) return false
  if (!query) return true

  const wanted = new URLSearchParams(query).get(VIEW_PARAM)
  const current = new URLSearchParams(search).get(VIEW_PARAM) ?? ADMIN_DEFAULT_TAB
  return wanted === current
}

/** The label of the entry being looked at, for the mobile title bar. */
export function activeLabel(sections: NavSection[], pathname: string, search: string): string | null {
  for (const section of sections) {
    for (const item of section.items) {
      if (isNavItemActive(item, pathname, search)) return item.label
    }
  }
  return null
}
