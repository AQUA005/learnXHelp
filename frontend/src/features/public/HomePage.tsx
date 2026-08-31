import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useBranding } from '@/lib/branding'
import type { UniversitySummary } from '@/lib/types'
import { EmptyState, Loading, initialsOf } from '@/components/ui'
import { Icon } from '@/components/icons'
import type { IconName } from '@/components/icons'

/** The universities using LearnX, and the way in to each of them. */
export default function HomePage() {
  const branding = useBranding()

  const universities = useQuery({
    queryKey: ['public', 'universities'],
    queryFn: () => api.get<UniversitySummary[]>('/api/public/universities'),
  })

  const items = universities.data ?? []

  return (
    <>
      <div className="hero-split">
        <section className="public-hero">
          <h1>{branding.tagline ?? 'Your campus, in one place'}</h1>
          <p className="muted">
            Class routines, a shared notes library, announcements, online exams and results.
            Choose your university to get started.
          </p>
          <div className="hero-actions">
            <Link className="btn" to="/signup">
              Create an account
            </Link>
            <Link className="btn btn-secondary" to="/signin">
              Sign in
            </Link>
          </div>
        </section>

        {/* What the account is actually for, in the reader's terms. */}
        <aside className="hero-panel">
          <h2>What you get</h2>
          <ul>
            {HIGHLIGHTS.map((item) => (
              <li key={item.title}>
                <span className="hero-mark" aria-hidden="true">
                  <Icon name={item.icon} />
                </span>
                <span>
                  <strong>{item.title}</strong>
                  <p>{item.body}</p>
                </span>
              </li>
            ))}
          </ul>
        </aside>
      </div>

      <div className="section-head">
        <h2>Choose your university</h2>
        <p className="small muted">Sign in through the university you belong to.</p>
      </div>

      {universities.isLoading ? (
        <Loading rows={3} label="Loading universities" />
      ) : items.length === 0 ? (
        <EmptyState
          title="No universities are listed yet"
          hint="Once a university is published it will appear here."
        />
      ) : (
        <div className="uni-grid">
          {items.map((university) => (
            <Link key={university.slug} className="uni-card" to={`/u/${university.slug}`}>
              {university.logoUrl ? (
                <img className="uni-logo" src={university.logoUrl} alt="" />
              ) : (
                <div className="uni-logo" aria-hidden="true">
                  {initialsOf(university.name)}
                </div>
              )}
              <div className="uni-card-body">
                <strong>{university.name}</strong>
                {university.shortDescription && (
                  <p className="small muted">{university.shortDescription}</p>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </>
  )
}

const HIGHLIGHTS: { icon: IconName; title: string; body: string }[] = [
  {
    icon: 'calendar',
    title: 'Your routine and class tests',
    body: 'Today at a glance, the full week behind it, and what is coming up.',
  },
  {
    icon: 'folder',
    title: 'A notes library your class builds',
    body: 'Shared material, checked by a teacher before it appears.',
  },
  {
    icon: 'exam',
    title: 'Online exams',
    body: 'Sit a paper in the browser and see the mark as soon as it is in.',
  },
  {
    icon: 'chart',
    title: 'Results you can read',
    body: 'Every assessment against the class average, not just a number.',
  },
]
