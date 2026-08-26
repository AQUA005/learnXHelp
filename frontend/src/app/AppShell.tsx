import { useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useSession } from '@/lib/session'
import type { Role } from '@/lib/types'
import { Icon } from '@/components/icons'
import { initialsOf } from '@/components/ui'
import { activeLabel, isNavItemActive, navigationFor } from './navigation'

export default function AppShell() {
  const { user, signOut } = useSession()
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()

  if (!user) return null

  const sections = navigationFor(user.role)
  const currentLabel = activeLabel(sections, location.pathname, location.search) ?? 'LearnX'

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

        <nav className="sidebar-nav" aria-label={`${roleLabel(user.role)} sections`}>
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
