import { CDSHero, CDSPageShell } from '../components/CDSPageShell'
import { OrderSummaryCompact } from '../components/OrderSummaryCard'
import { ReceiptMethodSelector } from '../components/ReceiptMethodSelector'
import { StatusIllustration } from '../components/StatusIllustration'
import type { CdsOrder, CdsStore } from '../types'

export function SendReceiptPage({ store, order }: { store: CdsStore; order: CdsOrder }) {
  return (
    <CDSPageShell
      store={store}
      infoMessage="You can close this screen after selecting an option."
      hero={
        <CDSHero headline="Send your receipt" subtitle="Choose how you'd like to receive your receipt.">
          <StatusIllustration className="mt-2 w-full max-w-md" />
        </CDSHero>
      }
    >
      <div className="flex flex-col gap-4">
        <OrderSummaryCompact order={order} />
        <ReceiptMethodSelector />
      </div>
    </CDSPageShell>
  )
}
