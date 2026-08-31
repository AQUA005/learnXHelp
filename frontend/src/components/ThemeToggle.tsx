import { useTheme } from '@/lib/theme'
import type { ThemeChoice } from '@/lib/theme'
import { Icon } from './icons'
import type { IconName } from './icons'

/**
 * Light, dark, or the machine's setting.
 *
 * Three buttons rather than a switch: "follow the system" is a real answer,
 * and a two-state control has nowhere to put it.
 */

const CHOICES: { id: ThemeChoice; label: string; icon: IconName }[] = [
  { id: 'light', label: 'Light', icon: 'sun' },
  { id: 'dark', label: 'Dark', icon: 'moon' },
  { id: 'system', label: 'Auto', icon: 'auto' },
]

export default function ThemeToggle() {
  const { choice, setChoice } = useTheme()

  return (
    <div className="theme-toggle" role="group" aria-label="Colour theme">
      {CHOICES.map((option) => (
        <button
          key={option.id}
          type="button"
          className={choice === option.id ? 'theme-option active' : 'theme-option'}
          aria-pressed={choice === option.id}
          title={option.label}
          onClick={() => setChoice(option.id)}
        >
          <Icon name={option.icon} />
          <span>{option.label}</span>
        </button>
      ))}
    </div>
  )
}
