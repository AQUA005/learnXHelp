import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '@/lib/api'
import type { UniversityProfile } from '@/lib/types'
import { Badge, Card, EmptyState, Loading, initialsOf } from '@/components/ui'

/**
 * One university's public page.
 *
 * An unpublished university answers 404 exactly as a nonexistent one does, so
 * this screen cannot tell them apart either — which is the point.
 */
export default function UniversityPublicPage() {
  const { slug = '' } = useParams<{ slug: string }>()

  const university = useQuery({
    queryKey: ['public', 'university', slug],
    queryFn: () => api.get<UniversityProfile>(`/api/public/universities/${slug}`),
  })

  if (university.isLoading) {
    return <Loading rows={4} label="Loading the university" />
  }

  if (university.isError || !university.data) {
    const missing = university.error instanceof ApiError && university.error.status === 404
    return (
      <EmptyState
        title={missing ? "That university isn't listed" : 'Could not load the university'}
        hint={missing ? 'Check the link, or pick one from the home page.' : 'Please try again.'}
      />
    )
  }

  const profile = university.data

  return (
    <>
      <header className="uni-head">
        {profile.logoUrl ? (
          <img className="uni-logo uni-logo-lg" src={profile.logoUrl} alt="" />
        ) : (
          <div className="uni-logo uni-logo-lg" aria-hidden="true">
            {initialsOf(profile.name)}
          </div>
        )}
        <div>
          <h1>{profile.name}</h1>
          {profile.description && <p className="muted">{profile.description}</p>}
        </div>
      </header>

      <div className="row" style={{ marginBottom: '1.5rem' }}>
        <Link className="btn" to={`/signup?university=${encodeURIComponent(profile.slug)}`}>
          Create an account
        </Link>
        <Link className="btn btn-secondary" to="/signin">
          Sign in
        </Link>
      </div>

      <div className="grid grid-2">
        {profile.departments.length > 0 && (
          <Card title="Departments">
            <div className="row">
              {profile.departments.map((department) => (
                <Badge key={department}>{department}</Badge>
              ))}
            </div>
          </Card>
        )}

        <Card title="Contact">
          <dl className="detail-list">
            {profile.contactEmail && (
              <>
                <dt>Email</dt>
                <dd>
                  <a href={`mailto:${profile.contactEmail}`}>{profile.contactEmail}</a>
                </dd>
              </>
            )}
            {profile.contactPhone && (
              <>
                <dt>Phone</dt>
                <dd>{profile.contactPhone}</dd>
              </>
            )}
            {profile.website && (
              <>
                <dt>Website</dt>
                <dd>
                  <a href={profile.website} rel="noreferrer noopener" target="_blank">
                    {profile.website}
                  </a>
                </dd>
              </>
            )}
            {profile.address && (
              <>
                <dt>Address</dt>
                <dd>{profile.address}</dd>
              </>
            )}
            {!profile.contactEmail &&
              !profile.contactPhone &&
              !profile.website &&
              !profile.address && <dd className="muted small">No contact details published.</dd>}
          </dl>
        </Card>
      </div>
    </>
  )
}
