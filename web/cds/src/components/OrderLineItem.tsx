import type { CdsLineItem } from '../types'
import { formatCurrency } from '../util/format'

/** One itemized row: quantity, name + modifiers/notes, amount. */
export function OrderLineItem({ item, showModifiers = true }: { item: CdsLineItem; showModifiers?: boolean }) {
  return (
    <div className="flex items-start gap-5 py-3">
      <span className="w-8 shrink-0 text-lg font-semibold tabular-nums text-textPrimary">{item.qty}</span>
      <div className="min-w-0 flex-1">
        <div className="text-lg font-semibold text-textPrimary">{item.name}</div>
        {showModifiers && item.modifiers && <div className="mt-0.5 text-sm text-textSecondary">{item.modifiers}</div>}
      </div>
      <span className="shrink-0 text-lg font-semibold tabular-nums text-textPrimary">
        {formatCurrency(item.amount)}
      </span>
    </div>
  )
}
