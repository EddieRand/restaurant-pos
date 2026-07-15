import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { RegionConfig } from '../api/admin'

type CdsConfig = NonNullable<RegionConfig['cdsConfig']>

type PreviewState = 'welcome' | 'order' | 'completed'

interface CdsPreviewProps {
  cds: CdsConfig
  currencySymbol?: string
  minorDigits?: number
}

// The CDS is designed for a 1280×720 landscape display; the preview renders the real app
// at that size and scales it down to fit, so the preview is a true 1:1 mapping.
const CDS_W = 1280
const CDS_H = 720

// Admin tab → the actual CDS screen it maps to.
const PAGE_FOR: Record<PreviewState, string> = {
  welcome: 'welcome',
  order: 'order',
  completed: 'success',
}

// In production the CDS is served same-origin at /cds/; in dev it lives on the POS server.
const CDS_BASE = import.meta.env.DEV
  ? (import.meta.env.VITE_CDS_BASE ?? 'http://localhost:8080/cds/')
  : '/cds/'

export function CdsPreview({ cds, currencySymbol = '$', minorDigits = 2 }: CdsPreviewProps) {
  const { t } = useTranslation()
  const [state, setState] = useState<PreviewState>('welcome')
  const boxRef = useRef<HTMLDivElement>(null)
  const iframeRef = useRef<HTMLIFrameElement>(null)
  const [scale, setScale] = useState(0.25)
  const [iframeReady, setIframeReady] = useState(0) // bump to retrigger a push

  // Keep the embedded CDS scaled exactly to the preview box width.
  useEffect(() => {
    const el = boxRef.current
    if (!el) return
    const apply = () => setScale(el.clientWidth / CDS_W)
    apply()
    const ro = new ResizeObserver(apply)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  // The embedded CDS announces readiness; bump a counter so the push effect fires.
  useEffect(() => {
    const onMsg = (e: MessageEvent) => {
      if (e.data && e.data.type === 'cds-preview-ready') setIframeReady(n => n + 1)
    }
    window.addEventListener('message', onMsg)
    return () => window.removeEventListener('message', onMsg)
  }, [])

  // Push the current (unsaved) config + currency to the preview so it updates as you type.
  useEffect(() => {
    iframeRef.current?.contentWindow?.postMessage(
      {
        type: 'cds-preview-config',
        currencySymbol,
        minorDigits,
        store: { name: cds.displayName || 'Store Name', logoUrl: cds.logoUrl || undefined },
        config: {
          welcomeTitle: cds.welcomeTitle || 'Welcome!',
          welcomeSubtitle: cds.welcomeSubtitle || 'Please review your order here.',
          completionTitle: cds.completionTitle || 'Payment successful',
          completionSubtitle: cds.completionSubtitle || 'Thank you. Your payment has been completed.',
          showOrderItems: cds.showOrderItems,
          showRunningTotal: cds.showRunningTotal,
          showModifiers: cds.showModifiers,
        },
      },
      '*',
    )
  }, [cds, currencySymbol, minorDigits, iframeReady, state])

  const tabs: { key: PreviewState; label: string }[] = [
    { key: 'welcome', label: t('cds.previewWelcome') },
    { key: 'order', label: t('cds.previewOrderState') },
    { key: 'completed', label: t('cds.previewCompletedState') },
  ]

  return (
    <div className="lg:sticky lg:top-8 space-y-3">
      <div className="flex gap-1 rounded-lg bg-gray-100 p-1 text-xs">
        {tabs.map(tab => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setState(tab.key)}
            className={`flex-1 rounded-md px-2 py-1.5 font-medium transition-colors ${
              state === tab.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {!cds.enabled && (
        <div className="rounded-md bg-amber-50 px-2 py-1 text-[11px] text-amber-600">
          {t('cds.enabled')}: {t('common.disable')}
        </div>
      )}

      <div
        ref={boxRef}
        className="aspect-video w-full overflow-hidden rounded-xl border border-gray-200 bg-[#FAF8F5] shadow-sm"
      >
        <iframe
          ref={iframeRef}
          key={state}
          title="CDS preview"
          src={`${CDS_BASE}?preview=${PAGE_FOR[state]}`}
          style={{
            width: CDS_W,
            height: CDS_H,
            border: 0,
            transformOrigin: 'top left',
            transform: `scale(${scale})`,
            pointerEvents: 'none',
          }}
        />
      </div>
      <p className="text-[11px] text-gray-400">{t('common.previewDisclaimer')}</p>
    </div>
  )
}
