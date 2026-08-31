import type { ReactNode } from 'react'
import { useBranding } from '@/lib/branding'

/**
 * The frame every account screen sits in.
 *
 * Two panes: colour on one side, the form on the other. The coloured pane
 * carries the one sentence worth saying and nothing else -- it is the reason
 * the form does not have to explain itself. It is decorative, so it is dropped
 * entirely on a narrow screen rather than stacked, where it would only push
 * the fields below the fold.
 */
export default function AuthLayout({
  eyebrow,
  headline,
  children,
}: {
  eyebrow?: string
  headline: string
  children: ReactNode
}) {
  const branding = useBranding()

  return (
    <div className="auth-split">
      <aside className="auth-art" aria-hidden="true">
        <div className="auth-art-light" />
        <div className="auth-art-body">
          {eyebrow && <p className="auth-art-eyebrow">{eyebrow}</p>}
          <h2 className="auth-art-headline">{headline}</h2>
        </div>
        <p className="auth-art-foot">{branding.siteName}</p>
      </aside>

      <div className="auth-pane">{children}</div>
    </div>
  )
}
