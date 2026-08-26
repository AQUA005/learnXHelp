/** A university as the platform console sees it, from `/api/master/universities`. */
export type ConsoleUniversity = {
  id: number
  name: string
  domain: string
  /** The public URL key. Fixed at creation. */
  slug: string
  description: string | null
  contactEmail: string | null
  contactPhone: string | null
  website: string | null
  address: string | null
  logoUrl: string | null
  published: boolean
  adminEmail: string | null
}
