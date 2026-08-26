import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useBranding } from '@/lib/branding'
import type { UniversitySummary } from '@/lib/types'
import { EmptyState, Loading, initialsOf } from '@/components/ui'

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
      <section className="public-hero">
        <h1>{branding.tagline ?? 'Your campus, in one place'}</h1>
        <p className="muted">
          Class routines, a shared notes library, announcements, online exams and results.
          Choose your university to get started.
        </p>
      </section>

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
