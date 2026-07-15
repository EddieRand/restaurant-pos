import type { CdsTotals } from '../types'
import { formatCurrency } from '../util/format'

interface TotalBreakdownProps {
  totals: CdsTotals
  /** Smaller text for compact summary cards. */
  compact?: boolean
}

/** Subtotal / discount / tax / service charge / (tip) rows. Discount shown in warm discount color. */
export function TotalBreakdown({ totals, compact = false }: TotalBreakdownProps) {
  const rowText = compact ? 'text-base' : 'text-base'
  return (
    <div className={`space-y-1.5 ${rowText}`}>
      <Row label="Subtotal" value={formatCurrency(totals.subtotal)} />
      {totals.discount > 0 && (
        <Row label="Discount" value={`-${formatCurrency(totals.discount)}`} valueClass="text-discount" />
      )}
      <Row label="Tax" value={formatCurrency(totals.tax)} />
      <Row label="Service Charge" value={formatCurrency(totals.serviceCharge)} />
      {totals.tip != null && totals.tip > 0 && <Row label="Tip" value={formatCurrency(totals.tip)} />}
    </div>
  )
}

function Row({ label, value, valueClass = 'text-textPrimary' }: { label: string; value: string; valueClass?: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-textSecondary">{label}</span>
      <span className={`font-medium tabular-nums ${valueClass}`}>{value}</span>
    </div>
  )
}
