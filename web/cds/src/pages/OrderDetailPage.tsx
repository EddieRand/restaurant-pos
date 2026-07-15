import { CDSHero, CDSPageShell } from '../components/CDSPageShell'
import { OrderSummaryCard } from '../components/OrderSummaryCard'
import { StatusIllustration } from '../components/StatusIllustration'
import type { CdsDisplayConfig, CdsOrder, CdsStore } from '../types'

export function OrderDetailPage({ store, order, config }: { store: CdsStore; order: CdsOrder; config: CdsDisplayConfig }) {
  return (
    <CDSPageShell
      store={store}
      hero={
        <CDSHero headline="Review your order" subtitle="Please confirm your items and totals below.">
          <StatusIllustration className="mt-2 w-full max-w-md" />
        </CDSHero>
      }
    >
      <OrderSummaryCard
        order={order}
        showItems={config.showOrderItems}
        showTotals={config.showRunningTotal}
        showModifiers={config.showModifiers}
        infoMessage="Items and totals update automatically as the cashier adds them."
      />
    </CDSPageShell>
  )
}
