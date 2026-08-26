import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api, primeCsrfToken } from './api'
import type { CurrentUser, Role } from './types'

/**
 * Who is signed in.
 *
 * Held in one place so route guards and the navigation both read the same
 * answer, and so a 401 anywhere clears it.
 */

type SessionState = {
  user: CurrentUser | null
  /** True until the first check of the existing session completes. */
  loading: boolean
  /** Email is the credential; the username is generated and never typed. */
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
  refresh: () => Promise<void>
  /** Applies a local change, such as a new avatar, without a round trip. */
  patchUser: (changes: Partial<CurrentUser>) => void
}

const SessionContext = createContext<SessionState | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setUser(await api.get<CurrentUser>('/api/auth/current-user'))
    } catch (error) {
      // A 401 simply means nobody is signed in yet.
      if (!(error instanceof ApiError && error.isUnauthenticated)) {
        console.error('Could not load the current user', error)
      }
      setUser(null)
    }
  }, [])

  useEffect(() => {
    let active = true
    void (async () => {
      await primeCsrfToken()
      await refresh()
      if (active) setLoading(false)
    })()
    return () => {
      active = false
    }
  }, [refresh])

  const signIn = useCallback(async (email: string, password: string) => {
    // The response is the signed-in user, so no second request is needed —
    // which is why anything added to it must be added to both of the server's
    // UserResponse construction paths.
    setUser(await api.post<CurrentUser>('/api/auth/login', { email, password }))
  }, [])

  const signOut = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } finally {
      setUser(null)
    }
  }, [])

  const patchUser = useCallback((changes: Partial<CurrentUser>) => {
    setUser((current) => (current ? { ...current, ...changes } : current))
  }, [])

  const value = useMemo<SessionState>(
    () => ({ user, loading, signIn, signOut, refresh, patchUser }),
    [user, loading, signIn, signOut, refresh, patchUser],
  )

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): SessionState {
  const context = useContext(SessionContext)
  if (!context) {
    throw new Error('useSession must be used inside a SessionProvider')
  }
  return context
}

/** The signed-in user, for screens that only render behind a guard. */
export function useCurrentUser(): CurrentUser {
  const { user } = useSession()
  if (!user) {
    throw new Error('No signed-in user; this screen should sit behind a route guard')
  }
  return user
}

/** Privilege order, mirroring the role hierarchy the server enforces. */
const ROLE_RANK: Record<Role, number> = {
  STUDENT: 0,
  CR: 1,
  TEACHER: 2,
  ADMIN: 3,
  SYSTEM_ADMIN: 4,
}

/**
 * Whether a role meets a minimum.
 *
 * This decides what to show, never what is allowed: every rule is enforced
 * again on the server.
 */
export function hasAtLeast(role: Role, minimum: Role): boolean {
  return ROLE_RANK[role] >= ROLE_RANK[minimum]
}
