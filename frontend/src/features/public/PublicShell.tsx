import { Link, Outlet } from 'react-router-dom'
import { useBranding } from '@/lib/branding'

/**
 * The frame around every page a visitor sees before signing in.
 *
 * Separate from `AppShell`, which assumes a signed-in user and a university to
 * scope its navigation to. There is neither here.
 */
export default function PublicShell() {
  const branding = useBranding()

  return (
    <div className="public-shell">
      <header className="public-header">
        <Link className="public-brand" to="/">
          {branding.logoUrl && <img className="public-brand-logo" src={branding.logoUrl} alt="" />}
          <span>{branding.siteName}</span>
        </Link>

        <nav className="row">
          <Link className="btn btn-secondary btn-sm" to="/signup">
            Create account
          </Link>
          <Link className="btn btn-sm" to="/signin">
            Sign in
          </Link>
        </nav>
      </header>

      <main className="public-main">
        <Outlet />
      </main>

      <footer className="public-footer">
        <span className="small muted">
          {branding.siteName}
          {branding.tagline ? ` — ${branding.tagline}` : ''}
        </span>
        {branding.supportEmail && (
          <a className="small" href={`mailto:${branding.supportEmail}`}>
            {branding.supportEmail}
          </a>
        )}
      </footer>
    </div>
  )
}
