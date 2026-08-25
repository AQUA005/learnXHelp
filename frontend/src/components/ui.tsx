import type { ReactNode } from 'react'

/**
 * Small building blocks shared across features.
 *
 * None of these render HTML from data. Text always goes through React, which
 * escapes it, so the class of injection the previous frontend was open to
 * cannot occur.
 */

export function Card({
  title,
  actions,
  children,
}: {
  title?: ReactNode
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card-head">
          {typeof title === 'string' ? <h2>{title}</h2> : title}
          {actions}
        </header>
      )}
      {children}
    </section>
  )
}

export function Alert({ kind, children }: { kind: 'error' | 'success' | 'info'; children: ReactNode }) {
  return (
    <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      {hint && <p className="small">{hint}</p>}
    </div>
  )
}

export function Loading({ rows = 3, label = 'Loading' }: { rows?: number; label?: string }) {
  return (
    <div aria-busy="true" aria-label={label}>
      {Array.from({ length: rows }, (_, i) => (
        <div
          key={i}
          className="skeleton"
          style={{ marginBottom: '0.5rem', width: `${100 - i * 12}%` }}
        />
      ))}
    </div>
  )
}

export function Badge({
  kind = 'default',
  children,
}: {
  kind?: 'default' | 'accent' | 'success' | 'warning' | 'danger'
  children: ReactNode
}) {
  const suffix = kind === 'default' ? '' : ` badge-${kind}`
  return <span className={`badge${suffix}`}>{children}</span>
}

export function Field({
  label,
  htmlFor,
  error,
  children,
}: {
  label: string
  htmlFor: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="field">
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {error && <div className="field-error">{error}</div>}
    </div>
  )
}

export function PageHeader({ title, description }: { title: string; description?: string }) {
  return (
    <header className="page-head">
      <h1>{title}</h1>
      {description && <p>{description}</p>}
    </header>
  )
}

/** Initials for an avatar placeholder. */
export function initialsOf(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}
