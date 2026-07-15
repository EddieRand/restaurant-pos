import { formatCurrency } from '../util/format'

interface TipOptionCardProps {
  percent?: number
  label: string
  amount?: number
  selected: boolean
  onSelect: () => void
  /** "No Tip" variant: no percent, no amount. */
  noTip?: boolean
}

/**
 * Tip preset card. Selected state uses the warm brown/beige theme (accent border + soft
 * fill) — never green — to stay consistent with the rest of the CDS.
 */
export function TipOptionCard({ percent, label, amount, selected, onSelect, noTip = false }: TipOptionCardProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={`flex flex-col items-center justify-center rounded-cds border-2 px-6 py-7 transition-colors duration-150 ${
        selected
          ? 'border-accent bg-accent-soft'
          : 'border-cdsborder bg-surface hover:border-accent/50'
      }`}
    >
      {noTip ? (
        <>
          <span className={`text-4xl font-bold ${selected ? 'text-accent-dark' : 'text-textPrimary'}`}>No Tip</span>
          <span className="mt-3 text-xl text-textSecondary">{label}</span>
        </>
      ) : (
        <>
          <span className={`text-5xl font-bold ${selected ? 'text-accent-dark' : 'text-textPrimary'}`}>{percent}%</span>
          <span className="mt-2 text-xl text-textSecondary">{label}</span>
          <span className="my-4 h-px w-16 bg-cdsborder" />
          <span className={`text-2xl font-semibold tabular-nums ${selected ? 'text-accent-dark' : 'text-accent'}`}>
            {amount != null ? formatCurrency(amount) : ''}
          </span>
        </>
      )}
    </button>
  )
}
