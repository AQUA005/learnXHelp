import { lazy, Suspense, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useSession } from '@/lib/session'
import { brandMarkUrl, useBranding } from '@/lib/branding'
import type { Role } from '@/lib/types'
import { Icon } from '@/components/icons'
import { initialsOf } from '@/components/ui'
import { activeLabel, isNavItemActive, navigationFor } from './navigation'

// Loaded only when somebody actually has something to report.
const ReportProblem = lazy(() => import('@/features/support/ReportProblem'))

export default function AppShell() {
  const { user, signOut } = useSession()
  const branding = useBranding()
  const [menuOpen, setMenuOpen] = useState(false)
  const [reporting, setReporting] = useState(false)
  const location = useLocation()

  if (!user) return null

  const sections = navigationFor(user.role)
  const currentLabel =
    activeLabel(sections, location.pathname, location.search) ?? branding.siteName

  return (
    <div className="app-shell">
      {menuOpen && (
        <button
          type="button"
          className="scrim"
          aria-label="Close the menu"
          onClick={() => setMenuOpen(false)}
        />
      )}

      <aside className={menuOpen ? 'sidebar open' : 'sidebar'}>
        <div className="sidebar-brand">
          <img className="brand-logo" src={brandMarkUrl(branding)} alt="" />
          <span>{branding.siteName}</span>
        </div>

        {/* Which school, as distinct from which product. */}
        {user.university && <div className="sidebar-tenant small muted">{user.university.name}</div>}

        <div className="sidebar-user">
          {user.profilePicUrl ? (
            <img className="avatar" src={user.profilePicUrl} alt="" />
          ) : (
            <div className="avatar" aria-hidden="true">
              {initialsOf(user.fullName)}
            </div>
          )}
          <div className="sidebar-user-meta">
            <strong>{user.fullName}</strong>
            <span>{roleLabel(user.role)}</span>
          </div>
        </div>

        <nav className="sidebar-nav" aria-label="Sections">
          {sections.map((section) => (
            <div className="nav-section" key={section.title}>
              <h2 className="nav-section-title">{section.title}</h2>
              {section.items.map((item) => {
                // Plain links rather than NavLink: several admin entries share
                // a path and differ only by the view they open, which NavLink
                // ignores when it decides which one is current.
                const active = isNavItemActive(item, location.pathname, location.search)
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    className={active ? 'nav-link active' : 'nav-link'}
                    aria-current={active ? 'page' : undefined}
                    onClick={() => setMenuOpen(false)}
                  >
                    <Icon name={item.icon} />
                    <span>{item.label}</span>
                  </Link>
                )
              })}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          {/* Every role, on every screen: whoever runs into a broken page is
              the one who can describe it, and that is usually a student. */}
          <button
            className="btn btn-quiet btn-block"
            onClick={() => {
              setReporting(true)
              setMenuOpen(false)
            }}
          >
            <Icon name="bug" />
            <span>Report a problem</span>
          </button>
          <button className="btn btn-secondary btn-block" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
          >
            Menu
          </button>
          <strong>{currentLabel}</strong>
        </header>

        <main className="page">
          <Outlet />
        </main>
      </div>

      {reporting && (
        <Suspense fallback={null}>
          <ReportProblem onClose={() => setReporting(false)} />
        </Suspense>
      )}
    </div>
  )
}

function roleLabel(role: Role): string {
  switch (role) {
    case 'STUDENT':
      return 'Student'
    case 'CR':
      return 'Class representative'
    case 'TEACHER':
      return 'Teacher'
    case 'ADMIN':
      return 'Administrator'
    case 'SYSTEM_ADMIN':
      return 'Platform owner'
  }
}
