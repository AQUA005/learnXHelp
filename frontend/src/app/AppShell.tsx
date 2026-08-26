import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useSession } from '@/lib/session'
import { useBranding } from '@/lib/branding'
import type { Role } from '@/lib/types'
import { initialsOf } from '@/components/ui'

/** A destination, and exactly which roles it is for. */
type NavItem = { to: string; label: string; roles: Role[] }

const MEMBER: Role[] = ['STUDENT', 'CR', 'TEACHER', 'ADMIN']
const STAFF: Role[] = ['TEACHER', 'ADMIN']

/**
 * Navigation is by explicit role, not by the privilege hierarchy.
 *
 * The hierarchy is right for authorization — an ADMIN may do what a TEACHER may
 * do — but wrong for navigation. A SYSTEM_ADMIN outranks a STUDENT and so was
 * shown "Class routine" and "My results", whose endpoints resolve the caller's
 * university and answer 403, because a platform owner belongs to none. They are
 * not a super-student; they are a different persona.
 */
const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', roles: [...MEMBER, 'SYSTEM_ADMIN'] },
  { to: '/schedule', label: 'Class routine', roles: MEMBER },
  { to: '/notes', label: 'Notes library', roles: MEMBER },
  { to: '/announcements', label: 'Announcements', roles: MEMBER },
  { to: '/exams', label: 'Exams', roles: MEMBER },
  { to: '/performance', label: 'My results', roles: ['STUDENT', 'CR'] },
  { to: '/gradebook', label: 'Gradebook', roles: STAFF },
  { to: '/moderation', label: 'Note approvals', roles: STAFF },
  { to: '/admin', label: 'Administration', roles: ['ADMIN'] },
  { to: '/platform', label: 'Platform', roles: ['SYSTEM_ADMIN'] },
  { to: '/profile', label: 'Profile', roles: [...MEMBER, 'SYSTEM_ADMIN'] },
]

export default function AppShell() {
  const { user, signOut } = useSession()
  const branding = useBranding()
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()

  if (!user) return null

  const visible = NAV_ITEMS.filter((item) => item.roles.includes(user.role))
  const currentLabel =
    visible.find((item) => item.to === location.pathname)?.label ?? branding.siteName

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
          {branding.logoUrl && <img className="brand-logo" src={branding.logoUrl} alt="" />}
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
          {visible.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              onClick={() => setMenuOpen(false)}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
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
