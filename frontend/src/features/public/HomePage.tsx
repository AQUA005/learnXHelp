import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import type { MouseEvent } from 'react'
import { api } from '@/lib/api'
import { useBranding } from '@/lib/branding'
import type { UniversitySummary } from '@/lib/types'
import { EmptyState, Loading, initialsOf } from '@/components/ui'

/**
 * The way in.
 *
 * Two questions and no prose: which university are you, and are you signing in
 * or starting out. Everything the old homepage explained is explained better by
 * the application itself once somebody is inside it, so the copy is one line
 * and the rest is glass, space and light.
 */
export default function HomePage() {
  const branding = useBranding()

  const universities = useQuery({
    queryKey: ['public', 'universities'],
    queryFn: () => api.get<UniversitySummary[]>('/api/public/universities'),
  })

  const items = universities.data ?? []

  return (
    <div className="landing">
      <section className="landing-hero">
        <h1 className="landing-title">{branding.tagline ?? 'Your campus, in one place'}</h1>

        <div className="landing-actions">
          <GlassButton to="/signin" label="Sign In" />
          <GlassButton to="/signup" label="Get Started" primary />
        </div>
      </section>

      <aside className="landing-panel" aria-label="Universities">
        <h2 className="landing-panel-title">Universities</h2>

        <div className="landing-scroll">
          {universities.isLoading ? (
            <Loading rows={4} label="Loading universities" />
          ) : items.length === 0 ? (
            <EmptyState icon="platform" title="None listed yet" />
          ) : (
            items.map((university, index) => (
              <Link
                key={university.slug}
                className="landing-uni"
                to={`/u/${university.slug}`}
                style={{ animationDelay: `${Math.min(index, 8) * 60 + 120}ms` }}
              >
                {university.logoUrl ? (
                  <img className="landing-uni-mark" src={university.logoUrl} alt="" />
                ) : (
                  <span className="landing-uni-mark" aria-hidden="true">
                    {initialsOf(university.name)}
                  </span>
                )}
                <span className="landing-uni-name">{university.name}</span>
              </Link>
            ))
          )}
        </div>
      </aside>
    </div>
  )
}

/**
 * A pane of glass that catches the light where the pointer is.
 *
 * The highlight follows the cursor through two custom properties rather than
 * through state: this repaints a gradient, not a React tree, and it stops
 * entirely on a device without a pointer.
 */
function GlassButton({ to, label, primary = false }: { to: string; label: string; primary?: boolean }) {
  function track(event: MouseEvent<HTMLAnchorElement>) {
    const bounds = event.currentTarget.getBoundingClientRect()
    event.currentTarget.style.setProperty('--x', `${event.clientX - bounds.left}px`)
    event.currentTarget.style.setProperty('--y', `${event.clientY - bounds.top}px`)
  }

  return (
    <Link
      className={primary ? 'glass-cta primary' : 'glass-cta'}
      to={to}
      onMouseMove={track}
    >
      <span>{label}</span>
    </Link>
  )
}
