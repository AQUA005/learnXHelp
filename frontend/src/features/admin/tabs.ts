/**
 * The administration screens.
 *
 * Kept beside the page but separate from it so the navigation can link
 * straight to one without pulling the whole admin bundle into the shell.
 */

export type AdminTab = 'approvals' | 'people' | 'classes' | 'routine' | 'metadata' | 'audit'

export const ADMIN_TABS: { id: AdminTab; label: string }[] = [
  { id: 'approvals', label: 'Account approvals' },
  { id: 'people', label: 'People' },
  { id: 'classes', label: 'Classes' },
  { id: 'routine', label: 'Routine sheets' },
  { id: 'metadata', label: 'Dropdown options' },
  { id: 'audit', label: 'Change history' },
]

/** What `/admin` shows when the address carries no view. */
export const ADMIN_DEFAULT_TAB: AdminTab = 'approvals'

/** The query parameter the admin screens are addressed by. */
export const VIEW_PARAM = 'view'

export function adminTabPath(tab: AdminTab): string {
  return `/admin?${VIEW_PARAM}=${tab}`
}

export function adminTabFrom(value: string | null): AdminTab {
  return ADMIN_TABS.some((tab) => tab.id === value) ? (value as AdminTab) : ADMIN_DEFAULT_TAB
}
