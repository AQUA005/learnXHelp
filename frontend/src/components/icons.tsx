import type { ReactElement } from 'react'

/**
 * The small line icons the sidebar uses.
 *
 * Drawn inline rather than pulled from an icon font: the shell is the first
 * thing painted after sign in, and a webfont there would either block it or
 * land late enough to shift the navigation.
 */

export type IconName =
  | 'home'
  | 'calendar'
  | 'megaphone'
  | 'folder'
  | 'exam'
  | 'chart'
  | 'gradebook'
  | 'shield'
  | 'approvals'
  | 'people'
  | 'classes'
  | 'options'
  | 'history'
  | 'platform'
  | 'user'
  | 'sun'
  | 'moon'
  | 'auto'
  | 'clock'
  | 'sparkle'
  | 'chevron'
  | 'eye'
  | 'eye-off'

const PATHS: Record<IconName, ReactElement> = {
  home: (
    <>
      <path d="M3.5 10.5 12 3.5l8.5 7" />
      <path d="M5.5 9.7V20h13V9.7" />
    </>
  ),
  calendar: (
    <>
      <rect x="3.5" y="5" width="17" height="15" rx="2" />
      <path d="M3.5 9.5h17M8 3.5v3M16 3.5v3" />
    </>
  ),
  megaphone: (
    <>
      <path d="M4 9.5h3.5L14 5.5v13L7.5 14.5H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z" />
      <path d="M17.5 8.5a5 5 0 0 1 0 7" />
    </>
  ),
  folder: (
    <path d="M3.5 7a1.5 1.5 0 0 1 1.5-1.5h3.6l2 2.2h7.9A1.5 1.5 0 0 1 20 9.2v8.3a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z" />
  ),
  exam: (
    <>
      <path d="M6 3.5h8l4 4v13H6z" />
      <path d="M13.5 3.5v4.5H18M9 13h6M9 16.5h4" />
    </>
  ),
  chart: (
    <>
      <path d="M4 4v16h16" />
      <path d="M7.5 15l3.5-4 3 2.5 4.5-6" />
    </>
  ),
  gradebook: (
    <>
      <rect x="5" y="4" width="14" height="16.5" rx="1.8" />
      <path d="M8.5 9h7M8.5 12.5h7M8.5 16h4" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3.5 19 6v5.5c0 4-2.9 7.3-7 8.9-4.1-1.6-7-4.9-7-8.9V6z" />
      <path d="M9 12l2.2 2.2L15.5 10" />
    </>
  ),
  approvals: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M8.3 12.2l2.6 2.6 4.8-5.2" />
    </>
  ),
  people: (
    <>
      <circle cx="9.5" cy="8.5" r="3" />
      <path d="M3.5 19.5c0-3 2.7-4.8 6-4.8s6 1.8 6 4.8" />
      <path d="M16 6.2a3 3 0 0 1 0 5.6M17.5 15.3c2 .6 3.3 2.1 3.3 4.2" />
    </>
  ),
  classes: (
    <>
      <rect x="3.5" y="4" width="7" height="7" rx="1.2" />
      <rect x="13.5" y="4" width="7" height="7" rx="1.2" />
      <rect x="3.5" y="14" width="7" height="6" rx="1.2" />
      <rect x="13.5" y="14" width="7" height="6" rx="1.2" />
    </>
  ),
  options: (
    <>
      <path d="M4 7.5h16M4 12h16M4 16.5h16" />
      <circle cx="9" cy="7.5" r="1.8" />
      <circle cx="15" cy="16.5" r="1.8" />
    </>
  ),
  history: (
    <>
      <path d="M3.8 12a8.2 8.2 0 1 0 2.5-5.9" />
      <path d="M3.5 4.5V9H8" />
      <path d="M12 8v4.4l3 1.8" />
    </>
  ),
  platform: (
    <>
      <path d="M4 20.5h16" />
      <path d="M5.5 20.5V8.2L12 4.5l6.5 3.7v12.3" />
      <path d="M9.5 20.5v-4.2h5v4.2M9.5 10.5h1.6M13 10.5h1.6M9.5 13.5h1.6M13 13.5h1.6" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8.5" r="3.4" />
      <path d="M4.8 20c0-3.5 3.2-5.6 7.2-5.6s7.2 2.1 7.2 5.6" />
    </>
  ),
  chevron: <path d="M6 9.5l6 6 6-6" />,
  eye: (
    <>
      <path d="M2.5 12s3.6-6 9.5-6 9.5 6 9.5 6-3.6 6-9.5 6-9.5-6-9.5-6z" />
      <circle cx="12" cy="12" r="3.2" />
    </>
  ),
  'eye-off': (
    <>
      <path d="M4 4l16 16" />
      <path d="M9.9 5.2A9.6 9.6 0 0 1 12 5c5.9 0 9.5 6 9.5 6a17 17 0 0 1-3.5 4.05M6.3 7.4A17 17 0 0 0 2.5 11s3.6 6 9.5 6a9.7 9.7 0 0 0 3.6-.7" />
      <path d="M9.9 10.1a3.2 3.2 0 0 0 4.3 4.3" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4" />
    </>
  ),
  moon: <path d="M20 14.2A8.2 8.2 0 0 1 9.8 4a8.2 8.2 0 1 0 10.2 10.2z" />,
  auto: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 3.5v17a8.5 8.5 0 0 0 0-17z" fill="currentColor" stroke="none" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 1.8" />
    </>
  ),
  sparkle: (
    <>
      <path d="M12 4.5l1.7 4.3 4.3 1.7-4.3 1.7L12 16.5l-1.7-4.3L6 10.5l4.3-1.7z" />
      <path d="M17.5 16.5l.8 1.9 1.9.8-1.9.8-.8 1.9-.8-1.9-1.9-.8 1.9-.8z" />
    </>
  ),
}

/** A decorative icon: the link's own text is what a screen reader announces. */
export function Icon({ name }: { name: IconName }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {PATHS[name]}
    </svg>
  )
}
