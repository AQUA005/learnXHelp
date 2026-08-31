import type { Role } from '@/lib/types'

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
  /** How many accounts belong to it, so the list shows size as well as name. */
  userCount: number
}

/** One person at one university, as the platform console shows them. */
export type TenantUser = {
  id: number
  fullName: string
  email: string
  role: Role
  approved: boolean
  department: string | null
  batch: string | null
  section: string | null
}

/** A university's roll, from `/api/master/universities/{id}/users`. */
export type TenantUsers = {
  total: number
  /** Every role is present, at zero if nobody holds it. */
  byRole: Record<Role, number>
  users: TenantUser[]
}

/** A report filed from anywhere on the platform. */
export type BugReport = {
  id: number
  title: string
  description: string
  reportedBy: string | null
  reporterEmail: string | null
  reporterRole: Role | null
  /** Null when the platform owner filed it, since they belong to no campus. */
  universityName: string | null
  pagePath: string | null
  createdAt: string
  status: 'PENDING' | 'REVIEWED' | 'RESOLVED' | string
}
