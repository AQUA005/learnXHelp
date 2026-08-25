import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from './api'

/** Brief confirmations and failures, shown in the corner. */

type ToastKind = 'info' | 'success' | 'error'
type Toast = { id: number; kind: ToastKind; message: string }

type ToastApi = {
  notify: (message: string, kind?: ToastKind) => void
  /** Reports a caught error, using the server's message where there is one. */
  reportError: (error: unknown, fallback?: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

const VISIBLE_FOR_MS = 4500

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const notify = useCallback((message: string, kind: ToastKind = 'info') => {
    const id = nextId.current++
    setToasts((current) => [...current, { id, kind, message }])
    window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id))
    }, VISIBLE_FOR_MS)
  }, [])

  const reportError = useCallback(
    (error: unknown, fallback = 'Something went wrong. Please try again.') => {
      if (error instanceof ApiError) {
        // A 401 is handled by the session; showing it as an error is noise.
        if (error.isUnauthenticated) return
        notify(error.message, 'error')
        return
      }
      console.error(error)
      notify(fallback, 'error')
    },
    [notify],
  )

  const value = useMemo<ToastApi>(() => ({ notify, reportError }), [notify, reportError])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-host" aria-live="polite" aria-atomic="false">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.kind}`}>
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast must be used inside a ToastProvider')
  }
  return context
}
