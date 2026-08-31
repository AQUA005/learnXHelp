import { Card } from '@/components/ui'
import { Icon } from '@/components/icons'
import type { IconName } from '@/components/icons'

/**
 * One number on a dashboard.
 *
 * The hint is the point of it: "3" and "3 · next at 8:30 am" cost the same
 * space, and only one of them answers the question the reader actually has.
 * The tone is for a number that is asking to be acted on -- a queue waiting
 * for approval -- and is left alone otherwise.
 */
export default function StatTile({
  icon,
  value,
  label,
  hint,
  tone,
}: {
  icon: IconName
  value: string | number
  label: string
  hint?: string | null
  tone?: 'warning' | 'success'
}) {
  return (
    <Card>
      <div className="stat-tile">
        <span className={tone ? `stat-mark stat-mark-${tone}` : 'stat-mark'} aria-hidden="true">
          <Icon name={icon} />
        </span>
        <div className="stat">
          <span className="stat-value mono">{value}</span>
          <span className="stat-label">{label}</span>
          {hint && <span className="stat-hint small muted">{hint}</span>}
        </div>
      </div>
    </Card>
  )
}
