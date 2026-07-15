import { CDSHero, CDSPageShell } from '../components/CDSPageShell'
import { OrderSummaryCard } from '../components/OrderSummaryCard'
import { PaymentStatusCard } from '../components/PaymentStatusCard'
import { StatusIllustration } from '../components/StatusIllustration'
import type { CdsOrder, CdsStore } from '../types'

export function PaymentProcessingPage({ store, order }: { store: CdsStore; order: CdsOrder }) {
  return (
    <CDSPageShell
      store={store}
      hero={
        <CDSHero headline="Processing payment" subtitle="Please wait while your payment is being completed.">
          <StatusIllustration className="mt-2 w-full max-w-md" />
        </CDSHero>
      }
    >
      <OrderSummaryCard
        order={order}
        footer={<PaymentStatusCard phase="processing" />}
        infoMessage="A receipt option will be shown after payment is complete."
      />
    </CDSPageShell>
  )
}
