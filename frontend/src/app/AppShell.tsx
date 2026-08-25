import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { hasAtLeast, useSession } from '@/lib/session'
import type { Role } from '@/lib/types'
import { initialsOf } from '@/components/ui'

/** A destination, and the least privileged role that may see it. */
type NavItem = { to: string; label: string; minimum: Role }

const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', minimum: 'STUDENT' },
  { to: '/schedule', label: 'Class routine', minimum: 'STUDENT' },
  { to: '/notes', label: 'Notes library', minimum: 'STUDENT' },
  { to: '/announcements', label: 'Announcements', minimum: 'STUDENT' },
  { to: '/exams', label: 'Exams', minimum: 'STUDENT' },
  { to: '/performance', label: 'My results', minimum: 'STUDENT' },
  { to: '/gradebook', label: 'Gradebook', minimum: 'TEACHER' },
  { to: '/moderation', label: 'Note approvals', minimum: 'TEACHER' },
  { to: '/admin', label: 'Administration', minimum: 'ADMIN' },
  { to: '/profile', label: 'Profile', minimum: 'STUDENT' },
]

export default function AppShell() {
  const { user, signOut } = useSession()
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()

  if (!user) return null

  const visible = NAV_ITEMS.filter((item) => hasAtLeast(user.role, item.minimum))
  const currentLabel =
    visible.find((item) => item.to === location.pathname)?.label ?? 'LearnX'

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
        <div className="sidebar-brand">LearnX</div>

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
