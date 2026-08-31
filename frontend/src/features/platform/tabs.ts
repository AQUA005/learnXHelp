/**
 * The platform owner's screens.
 *
 * Kept beside the page but separate from it, exactly as the administration
 * tabs are, so the navigation can link to one without pulling the whole
 * platform bundle into the shell.
 */

import { VIEW_PARAM } from '@/app/views'

export type PlatformTab = 'universities' | 'branding' | 'bugs' | 'broadcast'

export const PLATFORM_TABS: { id: PlatformTab; label: string }[] = [
  { id: 'universities', label: 'Universities' },
  { id: 'branding', label: 'Site branding' },
  { id: 'bugs', label: 'Bug reports' },
  { id: 'broadcast', label: 'Broadcast' },
]

/** What `/platform` shows when the address carries no view. */
export const PLATFORM_DEFAULT_TAB: PlatformTab = 'universities'

export function platformTabPath(tab: PlatformTab): string {
  return `/platform?${VIEW_PARAM}=${tab}`
}

export function platformTabFrom(value: string | null): PlatformTab {
  return PLATFORM_TABS.some((tab) => tab.id === value) ? (value as PlatformTab) : PLATFORM_DEFAULT_TAB
}
