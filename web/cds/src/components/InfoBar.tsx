import { Icon } from './Icon'

interface InfoBarProps {
  message: string
  /** Compact variant sits inside a card (smaller padding); default is the full-width bar. */
  compact?: boolean
}

/** Bottom helper message with an info icon, on a soft warm background. */
export function InfoBar({ message, compact = false }: InfoBarProps) {
  return (
    <div
      className={`flex items-center gap-3 rounded-cds-sm bg-accent-soft/70 ${
        compact ? 'px-4 py-3' : 'px-6 py-4'
      }`}
    >
      <Icon name="info" className="h-5 w-5 shrink-0 text-accent" />
      <span className={`text-textSecondary ${compact ? 'text-sm' : 'text-base'}`}>{message}</span>
    </div>
  )
}
