import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

/**
 * Light, dark, or whatever the machine says.
 *
 * The stylesheet reads one attribute, `data-theme`, which is always resolved
 * to `light` or `dark` — never to `system`. A script in the page head sets it
 * before the first paint from the same stored value this hook reads, so a
 * viewer who has chosen dark never sees a white page while the bundle loads.
 * This keeps it in step afterwards, including when the machine changes its
 * mind and the choice is `system`.
 */

export type ThemeChoice = 'light' | 'dark' | 'system'

/** Shared with the inline script in index.html. Changing one means changing both. */
const STORAGE_KEY = 'learnx.theme'

type ThemeState = {
  /** What the viewer asked for. */
  choice: ThemeChoice
  /** What that resolves to right now. */
  resolved: 'light' | 'dark'
  setChoice: (choice: ThemeChoice) => void
}

const ThemeContext = createContext<ThemeState | null>(null)

function storedChoice(): ThemeChoice {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' || value === 'system' ? value : 'system'
  } catch {
    // Storage can be blocked outright, in which case the machine decides.
    return 'system'
  }
}

function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [choice, setChoiceState] = useState<ThemeChoice>(storedChoice)
  const [systemDark, setSystemDark] = useState(systemPrefersDark)

  // Only matters while the choice is `system`, but the listener is cheap and
  // keeping it mounted avoids a stale answer the moment somebody switches back.
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  const resolved: 'light' | 'dark' =
    choice === 'system' ? (systemDark ? 'dark' : 'light') : choice

  useEffect(() => {
    document.documentElement.dataset.theme = resolved
  }, [resolved])

  const setChoice = useCallback((next: ThemeChoice) => {
    setChoiceState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // The choice still applies to this tab; it just will not be remembered.
    }
  }, [])

  const value = useMemo<ThemeState>(
    () => ({ choice, resolved, setChoice }),
    [choice, resolved, setChoice],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeState {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used inside a ThemeProvider')
  }
  return context
}
