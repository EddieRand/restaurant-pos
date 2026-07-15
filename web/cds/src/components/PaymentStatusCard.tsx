import type { CdsPayment } from '../types'
import { formatCurrency } from '../util/format'
import { Icon } from './Icon'

interface PaymentStatusCardProps {
  phase: 'processing' | 'success'
  payment?: CdsPayment
}

/**
 * Payment state card. Processing shows a warm-accent stepper; success shows the only
 * green element in the CDS (a success check) plus total paid / change.
 */
export function PaymentStatusCard({ phase, payment }: PaymentStatusCardProps) {
  if (phase === 'processing') {
    return (
      <div className="rounded-cds border border-cdsborder bg-surface px-8 py-9">
        <div className="flex items-center justify-center gap-3">
          <span className="h-4 w-4 rounded-full bg-accent" />
          <span className="h-0.5 w-16 bg-accent" />
          <span className="h-4 w-4 rounded-full bg-accent" />
          <span className="h-0.5 w-16 bg-cdsborder" />
          <span className="h-4 w-4 rounded-full border-2 border-cdsborder bg-surface" />
        </div>
        <div className="mt-7 text-center">
          <div className="text-3xl font-bold text-textPrimary">Processing</div>
          <div className="mt-2 text-lg text-textSecondary">Your cashier is finalizing the payment.</div>
        </div>
      </div>
    )
  }

  return (
    <div className="rounded-cds bg-cdssuccess/5 px-7 py-7">
      <div className="flex items-center gap-4">
        <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-cdssuccess text-white">
          <Icon name="check" className="h-8 w-8" strokeWidth={2.5} />
        </span>
        <div>
          <div className="text-2xl font-bold text-textPrimary">Payment successful</div>
          <div className="mt-1 text-lg text-textSecondary">Thank you for your payment.</div>
        </div>
      </div>

      {payment && (
        <div className="mt-6 space-y-3 border-t border-cdsborder/70 pt-5 text-lg">
          <div className="flex items-center justify-between">
            <span className="text-textSecondary">Total paid</span>
            <span className="font-semibold tabular-nums text-textPrimary">{formatCurrency(payment.totalPaid)}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-textSecondary">Change</span>
            <span className="font-semibold tabular-nums text-cdssuccess">{formatCurrency(payment.change)}</span>
          </div>
        </div>
      )}
    </div>
  )
}
