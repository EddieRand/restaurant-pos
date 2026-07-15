import { useState } from 'react'
import { CDSPageShell } from '../components/CDSPageShell'
import { Icon } from '../components/Icon'
import { TipOptionCard } from '../components/TipOptionCard'
import { mockTipBase, mockTipOptions } from '../data/mockOrder'
import type { CdsOrder, CdsStore, CdsTipOption } from '../types'
import { formatCurrency } from '../util/format'

// Selection index: 0..n-1 = preset option, 'none' = No Tip. Defaults to 20% (index 2),
// matching the reference highlighted card.
type Selection = number | 'none'

// Preset tip percentages mirror the POS TipConfig defaults (calc base = subtotal).
const TIP_PRESETS: { percent: number; label: string }[] = [
  { percent: 15, label: 'Good' },
  { percent: 18, label: 'Great' },
  { percent: 20, label: 'Best' },
]

export function TipPage({ store, order }: { store: CdsStore; order?: CdsOrder }) {
  const [selected, setSelected] = useState<Selection>(2)

  // Live: derive tips from the real order subtotal; otherwise use mock figures.
  const base = order ? order.totals.subtotal : mockTipBase
  const orderTotal = order ? order.totals.total : mockTipBase
  const options: CdsTipOption[] = order
    ? TIP_PRESETS.map((p) => ({ percent: p.percent, label: p.label, amount: Math.round(base * p.percent) / 100 }))
    : mockTipOptions

  const selectedTip = typeof selected === 'number' ? options[selected].amount : 0
  const totalWithTip = orderTotal + selectedTip

  return (
    <CDSPageShell store={store} variant="centered" headerShowDate>
      <div className="text-center">
        <h1 className="text-6xl font-bold tracking-tight text-textPrimary">Add a tip?</h1>
        <p className="mt-3 text-2xl text-textSecondary">Thank you for your support!</p>
      </div>

      <div className="mt-8 grid grid-cols-4 gap-5">
        {options.map((opt, i) => (
          <TipOptionCard
            key={opt.percent}
            percent={opt.percent}
            label={opt.label}
            amount={opt.amount}
            selected={selected === i}
            onSelect={() => setSelected(i)}
          />
        ))}
        <TipOptionCard label="Thanks" noTip selected={selected === 'none'} onSelect={() => setSelected('none')} />
      </div>

      <button
        type="button"
        className="mt-5 flex w-full items-center justify-between rounded-cds border border-cdsborder bg-surface px-7 py-5"
      >
        <span className="flex items-center gap-4">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-accent-soft text-accent">
            <Icon name="pencil" className="h-6 w-6" />
          </span>
          <span className="text-2xl font-medium text-textPrimary">Custom Tip</span>
        </span>
        <span className="flex items-center gap-3 text-2xl font-semibold text-accent">
          {formatCurrency(0)}
          <Icon name="chevronRight" className="h-6 w-6 text-textSecondary" />
        </span>
      </button>

      <div className="mt-6 flex items-center justify-center gap-16 border-t border-cdsborder pt-6">
        <div className="text-center">
          <div className="text-xl text-textSecondary">Total</div>
          <div className="mt-1 text-4xl font-bold tabular-nums text-accent-dark">{formatCurrency(orderTotal)}</div>
        </div>
        <Icon name="chevronRight" className="h-8 w-8 text-textSecondary" />
        <div className="text-center">
          <div className="text-xl text-textSecondary">Total with Tip</div>
          <div className="mt-1 text-4xl font-bold tabular-nums text-accent-dark">
            {formatCurrency(totalWithTip)}
          </div>
        </div>
      </div>
    </CDSPageShell>
  )
}
