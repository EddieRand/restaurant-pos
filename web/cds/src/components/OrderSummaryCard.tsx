import type { CdsOrder } from '../types'
import { formatCurrency } from '../util/format'
import { InfoBar } from './InfoBar'
import { OrderBadge } from './OrderBadge'
import { OrderLineItem } from './OrderLineItem'
import { TotalBreakdown } from './TotalBreakdown'

interface OrderSummaryCardProps {
  order: CdsOrder
  /** Show the itemized list (Order Detail page). Compact mode hides items. */
  showItems?: boolean
  /** Show the totals breakdown + grand total. */
  showTotals?: boolean
  /** Show per-item modifiers / notes under each line. */
  showModifiers?: boolean
  /** Optional footer rendered below the total (e.g. payment status card). */
  footer?: React.ReactNode
  /** Optional helper message rendered as a compact info bar inside the card bottom. */
  infoMessage?: string
}

/** Order header + badges, optional items, total breakdown, and large grand total. */
export function OrderSummaryCard({
  order,
  showItems = false,
  showTotals = true,
  showModifiers = true,
  footer,
  infoMessage,
}: OrderSummaryCardProps) {
  return (
    <div className="cds-card flex flex-col gap-4 px-7 py-6">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-3xl font-bold tracking-tight text-textPrimary">Order #{order.number}</h2>
        <div className="flex items-center gap-2">
          <OrderBadge icon="user" label={order.type} />
          {order.tableLabel && <OrderBadge icon="table" label={order.tableLabel} />}
        </div>
      </div>

      {showItems && (
        <>
          <div className="flex items-center justify-between text-sm font-medium uppercase tracking-wide text-textSecondary">
            <span>Qty&nbsp;&nbsp;Item</span>
            <span>Amount</span>
          </div>
          <div className="divide-y divide-cdsborder/70 border-y border-cdsborder/70">
            {order.items.map((item, i) => (
              <OrderLineItem key={i} item={item} showModifiers={showModifiers} />
            ))}
          </div>
        </>
      )}

      {showTotals && (
        <>
          <TotalBreakdown totals={order.totals} compact={!showItems} />
          <div className="flex items-end justify-between border-t border-cdsborder/70 pt-3">
            <span className="text-2xl font-bold text-textPrimary">Total</span>
            <span className="text-4xl font-bold tabular-nums text-textPrimary">
              {formatCurrency(order.totals.total)}
            </span>
          </div>
        </>
      )}

      {footer}
      {infoMessage && <InfoBar message={infoMessage} compact />}
    </div>
  )
}

/** Compact order header used as the small top card on the Send Receipt page. */
export function OrderSummaryCompact({ order }: { order: CdsOrder }) {
  return (
    <div className="cds-card flex flex-col gap-3 px-6 py-5">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-2xl font-bold tracking-tight text-textPrimary">Order #{order.number}</h2>
        <div className="flex items-center gap-2">
          <OrderBadge icon="user" label={order.type} />
          {order.tableLabel && <OrderBadge icon="table" label={order.tableLabel} />}
        </div>
      </div>
      <TotalBreakdown totals={order.totals} compact />
      <div className="flex items-center justify-between border-t border-cdsborder/70 pt-3">
        <span className="text-xl font-bold text-textPrimary">Total</span>
        <span className="text-2xl font-bold tabular-nums text-accent">
          {formatCurrency(order.totals.total)}
        </span>
      </div>
    </div>
  )
}
