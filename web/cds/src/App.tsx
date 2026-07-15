import { useEffect, useState } from 'react'
import { fetchCdsState, type LiveCdsState } from './data/api'
import { mockOrder, mockPayment } from './data/mockOrder'
import { mockStore } from './data/store'
import { setCurrency } from './util/format'
import { DEFAULT_DISPLAY_CONFIG, type CdsDisplayConfig, type CdsOrder, type CdsPage, type CdsPayment, type CdsStore } from './types'
import { WelcomePage } from './pages/WelcomePage'
import { OrderDetailPage } from './pages/OrderDetailPage'
import { TipPage } from './pages/TipPage'
import { PaymentProcessingPage } from './pages/PaymentProcessingPage'
import { PaymentSuccessPage } from './pages/PaymentSuccessPage'
import { SendReceiptPage } from './pages/SendReceiptPage'

const PAGES: { key: CdsPage; label: string }[] = [
  { key: 'welcome', label: 'Welcome' },
  { key: 'order', label: 'Order' },
  { key: 'tip', label: 'Tip' },
  { key: 'processing', label: 'Processing' },
  { key: 'success', label: 'Success' },
  { key: 'receipt', label: 'Receipt' },
]

const POLL_MS = 1500

function initialPage(): CdsPage {
  const q = new URLSearchParams(window.location.search).get('page')
  return PAGES.some((p) => p.key === q) ? (q as CdsPage) : 'welcome'
}

function switcherEnabled(): boolean {
  if (import.meta.env.DEV) return true
  return new URLSearchParams(window.location.search).has('dev')
}

/**
 * `?preview=<page>` renders a single fixed screen for the admin preview iframe, and accepts
 * live config/currency overrides via postMessage so editing the admin form updates the
 * preview instantly. The page is forced and the dev switcher hidden.
 */
function previewPage(): CdsPage | null {
  const q = new URLSearchParams(window.location.search).get('preview')
  return q && PAGES.some((p) => p.key === q) ? (q as CdsPage) : null
}

interface PreviewOverride {
  config?: Partial<CdsDisplayConfig>
  store?: CdsStore
}

/**
 * Customer Display root. Polls the POS for live order/phase/config and renders the matching
 * screen. Falls back to mock data when the backend is unreachable (with a dev switcher).
 */
export default function App() {
  const [live, setLive] = useState<LiveCdsState | null>(null)
  const [mockPage, setMockPage] = useState<CdsPage>(initialPage)
  const [override, setOverride] = useState<PreviewOverride>({})
  const forced = previewPage()

  useEffect(() => {
    let active = true
    const controller = new AbortController()
    const tick = async () => {
      const state = await fetchCdsState(controller.signal)
      if (!active) return
      if (state) setCurrency(state.currencySymbol, state.minorDigits)
      setLive(state)
    }
    tick()
    const id = setInterval(tick, POLL_MS)
    return () => {
      active = false
      controller.abort()
      clearInterval(id)
    }
  }, [])

  // Admin preview: receive live config/currency overrides from the parent admin page.
  useEffect(() => {
    if (!forced) return
    const handler = (e: MessageEvent) => {
      const d = e.data
      if (!d || d.type !== 'cds-preview-config') return
      if (d.currencySymbol) setCurrency(String(d.currencySymbol), Number(d.minorDigits ?? 2))
      setOverride({ config: d.config, store: d.store })
    }
    window.addEventListener('message', handler)
    window.parent?.postMessage({ type: 'cds-preview-ready' }, '*')
    return () => window.removeEventListener('message', handler)
  }, [forced])

  const liveConfig = live?.config ?? DEFAULT_DISPLAY_CONFIG
  const config: CdsDisplayConfig = { ...liveConfig, ...(override.config ?? {}) }

  if (forced) {
    const store = override.store ?? live?.store ?? mockStore
    return renderPage(forced, store, live?.order ?? null, live?.payment ?? null, config)
  }

  if (live) {
    return renderPage(live.page, live.store, live.order, live.payment, config)
  }

  return (
    <>
      {renderPage(mockPage, mockStore, mockOrder, mockPayment, DEFAULT_DISPLAY_CONFIG)}
      {switcherEnabled() && <PagePreviewSwitcher page={mockPage} onChange={setMockPage} />}
    </>
  )
}

function renderPage(
  page: CdsPage,
  store: CdsStore,
  order: CdsOrder | null,
  payment: CdsPayment | null,
  config: CdsDisplayConfig,
) {
  // Pages that need an order fall back to the mock order if the POS hasn't sent one yet.
  const safeOrder = order ?? mockOrder
  switch (page) {
    case 'welcome':
      return <WelcomePage store={store} config={config} />
    case 'order':
      return <OrderDetailPage store={store} order={safeOrder} config={config} />
    case 'tip':
      return <TipPage store={store} order={order ?? undefined} />
    case 'processing':
      return <PaymentProcessingPage store={store} order={safeOrder} />
    case 'success':
      return <PaymentSuccessPage store={store} order={safeOrder} payment={payment ?? mockPayment} config={config} />
    case 'receipt':
      return <SendReceiptPage store={store} order={safeOrder} />
  }
}

/** Dev-only floating control to preview each CDS state; not part of the customer UI. */
function PagePreviewSwitcher({ page, onChange }: { page: CdsPage; onChange: (p: CdsPage) => void }) {
  return (
    <div className="fixed bottom-3 left-1/2 z-50 flex -translate-x-1/2 gap-1 rounded-full border border-cdsborder bg-surface/95 p-1 shadow-cds backdrop-blur print:hidden">
      {PAGES.map((p) => (
        <button
          key={p.key}
          type="button"
          onClick={() => onChange(p.key)}
          className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
            page === p.key ? 'bg-accent text-white' : 'text-textSecondary hover:text-textPrimary'
          }`}
        >
          {p.label}
        </button>
      ))}
    </div>
  )
}
