import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { PlatformBranding } from './types'

/** Shown while the real branding loads, and if it cannot be loaded at all. */
const FALLBACK: PlatformBranding = {
  siteName: 'LearnX',
  tagline: null,
  logoUrl: null,
  iconUrl: null,
  supportEmail: null,
}

/**
 * LearnX's own name and logo.
 *
 * The endpoint is public, so one hook serves both the pre-login site and the
 * signed-in shell. Branding changes about once a year, so it is fetched once
 * per page load and never refetched.
 */
export function useBranding(): PlatformBranding {
  const query = useQuery({
    queryKey: ['branding'],
    queryFn: () => api.get<PlatformBranding>('/api/public/branding'),
    staleTime: Infinity,
  })
  return query.data ?? FALLBACK
}
