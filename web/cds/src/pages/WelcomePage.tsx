import { CDSHero, CDSPageShell } from '../components/CDSPageShell'
import { Icon, type IconName } from '../components/Icon'
import { StatusIllustration } from '../components/StatusIllustration'
import type { CdsDisplayConfig, CdsStore } from '../types'

const CARDS: { icon: IconName; title: string; desc: string }[] = [
  { icon: 'monitor', title: 'Your order will appear here', desc: "As your items are added, you'll see them listed on this screen." },
  { icon: 'list', title: 'Order details', desc: 'Review item names, quantities, and any special instructions.' },
  { icon: 'calculator', title: 'Subtotal, tax and total', desc: 'See a clear breakdown of your subtotal, tax, and total.' },
  { icon: 'receipt', title: 'Receipt options', desc: 'Your receipt preference and details will be shown here.' },
]

export function WelcomePage({ store, config }: { store: CdsStore; config: CdsDisplayConfig }) {
  return (
    <CDSPageShell
      store={store}
      infoMessage="When the cashier starts your order, items and totals will appear automatically."
      hero={
        <CDSHero headline={config.welcomeTitle} subtitle={config.welcomeSubtitle}>
          <StatusIllustration className="mt-2 w-full max-w-md" />
        </CDSHero>
      }
    >
      <div className="flex flex-col gap-3.5">
        {CARDS.map((c) => (
          <div key={c.title} className="cds-card flex items-center gap-4 p-5">
            <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-accent-soft text-accent">
              <Icon name={c.icon} className="h-7 w-7" strokeWidth={1.75} />
            </span>
            <div>
              <div className="text-xl font-semibold text-textPrimary">{c.title}</div>
              <div className="mt-0.5 text-base text-textSecondary">{c.desc}</div>
            </div>
          </div>
        ))}
      </div>
    </CDSPageShell>
  )
}
