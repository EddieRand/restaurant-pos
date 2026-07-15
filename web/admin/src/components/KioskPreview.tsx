import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { RegionConfig } from '../api/admin'

type KioskConfig = NonNullable<RegionConfig['kioskConfig']>

type PreviewState = 'welcome' | 'order' | 'completed'

export function KioskPreview({ kiosk }: { kiosk: KioskConfig }) {
  const { t } = useTranslation()
  const [state, setState] = useState<PreviewState>('welcome')

  const textScale = kiosk.accessibilityEnabled ? 'text-base' : 'text-xs'
  const titleScale = kiosk.accessibilityEnabled ? 'text-2xl' : 'text-lg'

  const items = [
    { name: `${t('cds.previewSampleItem')} A`, qty: 2, modifier: t('cds.previewModifier') },
    { name: `${t('cds.previewSampleItem')} B`, qty: 1, modifier: null },
  ]

  const PAYMENT_ICONS: Record<string, string> = {
    CASH: '💵', CARD: '💳', QR_PAY: '📱', MEMBERSHIP: '🎟️',
  }

  const tabs: { key: PreviewState; label: string }[] = [
    { key: 'welcome', label: t('kiosk.previewWelcome') },
    { key: 'order', label: t('kiosk.previewOrder') },
    { key: 'completed', label: t('kiosk.previewCompleted') },
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

      <div className="aspect-[3/4] w-full overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        {state === 'welcome' ? (
          <div className="relative flex h-full flex-col items-center justify-center text-center text-white" style={{ backgroundImage: 'url(/previews/welcome-bg.svg)', backgroundSize: 'cover', backgroundPosition: 'center' }}>
            <div className="absolute inset-0 bg-black/20" />
            {kiosk.showWelcomeScreen ? (
              <div className="relative space-y-3 rounded-2xl bg-black/20 px-6 py-5 backdrop-blur-sm">
                <div className={`${titleScale} font-bold tracking-wide drop-shadow`}>{kiosk.welcomeTitle || '—'}</div>
                {kiosk.welcomeSubtitle && <div className={`${textScale} opacity-90 drop-shadow`}>{kiosk.welcomeSubtitle}</div>}
                <div className="mx-auto mt-2 w-fit rounded-full bg-brand-500 px-5 py-1.5 text-[11px] font-semibold shadow">
                  {t('kiosk.previewOrder')}
                </div>
              </div>
            ) : (
              <div className={`relative ${titleScale} font-bold drop-shadow opacity-50`}>{t('common.disable')}</div>
            )}
            {kiosk.showPromotionBanner && kiosk.promotionBannerUrl && (
              <div
                className="absolute bottom-3 left-3 right-3 h-12 overflow-hidden rounded-lg bg-white/20 bg-cover bg-center ring-1 ring-white/30"
                style={{ backgroundImage: `url(${kiosk.promotionBannerUrl})` }}
              />
            )}
          </div>
        ) : state === 'order' ? (
          <div className="flex h-full flex-col bg-gray-50 text-gray-900">
            <div className={`border-b border-gray-100 bg-white px-3 py-2 font-semibold ${textScale}`}>{t('kiosk.sectionWelcome')}</div>
            <div className={`flex-1 space-y-2 overflow-hidden px-3 py-2.5 ${textScale}`}>
              {items.map((item, i) => (
                <div key={i} className="flex items-center gap-2.5 rounded-lg bg-white px-2.5 py-2 shadow-sm">
                  <div className="h-9 w-9 shrink-0 rounded-md bg-brand-50 flex items-center justify-center text-brand-400 text-[10px] font-semibold">
                    {item.name.slice(-1)}
                  </div>
                  <div className="flex-1">
                    <div className="font-medium">{item.name} ×{item.qty}</div>
                    {item.modifier && <div className="text-[10px] text-gray-400">{item.modifier}</div>}
                  </div>
                </div>
              ))}
            </div>
            <div className={`flex items-center justify-between border-t border-gray-100 bg-white px-3 py-2.5 font-semibold ${textScale}`}>
              <span className="text-gray-500 text-xs">{t('cds.previewTotalLabel')}</span>
              <span className="text-brand-600">$ 0.00</span>
            </div>
          </div>
        ) : (
          <div className="relative flex h-full flex-col items-center bg-gray-50 text-center text-gray-900">
            <div className="absolute inset-0 h-2/5" style={{ backgroundImage: 'url(/previews/welcome-bg.svg)', backgroundSize: 'cover', backgroundPosition: 'center' }}>
              <div className="absolute inset-0 bg-black/30" />
            </div>

            <div className="relative z-10 flex flex-col items-center gap-1.5 pt-5 text-white">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-400 text-base shadow">✓</div>
              <div className={`${titleScale} font-bold tracking-wide drop-shadow`}>{kiosk.completionTitle || '—'}</div>
              {kiosk.completionSubtitle && <div className={`${textScale} opacity-90 drop-shadow`}>{kiosk.completionSubtitle}</div>}
            </div>

            {/* Pickup number — the dominant element, ticket-style card */}
            <div className="relative z-10 mt-6 flex w-full flex-1 flex-col items-center justify-center px-6">
              <div className="w-full max-w-[200px] rounded-2xl border border-gray-100 bg-white px-5 py-5 shadow-lg">
                <div className="text-[11px] font-semibold uppercase tracking-widest text-gray-400">
                  {t('kiosk.previewPickupLabel')}
                </div>
                <div className="mt-1.5 text-6xl font-black leading-none tracking-tight text-brand-600">
                  {t('kiosk.previewPickupCode')}
                </div>
              </div>
            </div>

            {kiosk.enabledPaymentMethods.length > 0 && (
              <div className="relative z-10 flex flex-wrap justify-center gap-1.5 px-4 pb-5">
                <span className="text-[10px] text-gray-400 self-center">{t('kiosk.previewPayWith')}</span>
                {kiosk.enabledPaymentMethods.map(m => (
                  <span key={m} className="rounded-full bg-gray-100 px-2.5 py-1 text-[10px] text-gray-600 ring-1 ring-gray-200">
                    {PAYMENT_ICONS[m] ?? ''} {t(`kiosk.payment.${m.toLowerCase()}`)}
                  </span>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
      <p className="text-[11px] text-gray-400">{t('common.previewDisclaimer')}</p>
    </div>
  )
}
