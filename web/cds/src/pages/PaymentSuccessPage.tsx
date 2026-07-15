import { CDSHero, CDSPageShell } from '../components/CDSPageShell'
import { OrderSummaryCard } from '../components/OrderSummaryCard'
import { PaymentStatusCard } from '../components/PaymentStatusCard'
import { StatusIllustration } from '../components/StatusIllustration'
import type { CdsDisplayConfig, CdsOrder, CdsPayment, CdsStore } from '../types'

export function PaymentSuccessPage({
  store,
  order,
  payment,
  config,
}: {
  store: CdsStore
  order: CdsOrder
  payment: CdsPayment
  config: CdsDisplayConfig
}) {
  return (
    <CDSPageShell
      store={store}
      hero={
        <CDSHero headline={config.completionTitle} subtitle={config.completionSubtitle}>
          <StatusIllustration className="mt-2 w-full max-w-md" />
        </CDSHero>
      }
    >
      <OrderSummaryCard
        order={order}
        footer={<PaymentStatusCard phase="success" payment={payment} />}
        infoMessage="Receipt options will be shown next."
      />
    </CDSPageShell>
  )
}
