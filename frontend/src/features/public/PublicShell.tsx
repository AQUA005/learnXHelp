import { Link, Outlet, useLocation } from 'react-router-dom'
import { brandMarkUrl, useBranding } from '@/lib/branding'

/**
 * The frame around every page a visitor sees before signing in.
 *
 * Separate from `AppShell`, which assumes a signed-in user and a university to
 * scope its navigation to. There is neither here.
 *
 * On the landing page the header carries the mark and nothing else: the two
 * actions are the page, and repeating them in a corner only competes with
 * them. Every other public page keeps them, because there they are the only
 * way through.
 */
export default function PublicShell() {
  const branding = useBranding()
  const isLanding = useLocation().pathname === '/'

  return (
    <div className={isLanding ? 'public-shell landing-shell' : 'public-shell'}>
      <header className="public-header">
        <Link className="public-brand" to="/">
          <img className="public-brand-logo" src={brandMarkUrl(branding)} alt="" />
          <span>{branding.siteName}</span>
        </Link>

        {!isLanding && (
          <nav className="row">
            <Link className="btn btn-secondary btn-sm" to="/signup">
              Create account
            </Link>
            <Link className="btn btn-sm" to="/signin">
              Sign in
            </Link>
          </nav>
        )}
      </header>

      <main className="public-main">
        <Outlet />
      </main>

      {!isLanding && (
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
      )}
    </div>
  )
}
