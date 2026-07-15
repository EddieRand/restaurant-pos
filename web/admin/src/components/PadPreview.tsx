import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { RegionConfig } from '../api/admin'

type PadConfig = RegionConfig['padConfig']

type PreviewState = 'idle' | 'order'

export function PadPreview({ pad }: { pad: PadConfig }) {
  const { t } = useTranslation()
  const [state, setState] = useState<PreviewState>('idle')

  const items = [
    { name: `${t('cds.previewSampleItem')} A`, price: 12.0, modifier: t('cds.previewModifier') },
    { name: `${t('cds.previewSampleItem')} B`, price: 8.5, modifier: null },
    { name: `${t('cds.previewSampleItem')} C`, price: 6.0, modifier: null },
    { name: `${t('cds.previewSampleItem')} D`, price: 15.0, modifier: null },
    { name: `${t('cds.previewSampleItem')} E`, price: 9.0, modifier: null },
    { name: `${t('cds.previewSampleItem')} F`, price: 4.5, modifier: null },
  ]
  const cart = [
    { name: `${t('cds.previewSampleItem')} A`, qty: 2, price: 12.0, modifier: t('cds.previewModifier') },
    { name: `${t('cds.previewSampleItem')} B`, qty: 1, price: 8.5, modifier: null },
  ]
  const total = cart.reduce((s, i) => s + i.qty * i.price, 0)

  const tabs: { key: PreviewState; label: string }[] = [
    { key: 'idle', label: t('pad.previewIdle') },
    { key: 'order', label: t('pad.previewOrdering') },
  ]

  return (
    <div className="space-y-3">
      <div className="flex gap-1 rounded-lg bg-gray-100 p-1 text-xs w-64">
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

      <div className="aspect-video w-full overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        {state === 'idle' ? (
          <div className="relative flex h-full flex-col items-center justify-center gap-3 text-center text-white" style={{ backgroundImage: 'url(/previews/pad-idle-bg.svg)', backgroundSize: 'cover', backgroundPosition: 'center' }}>
            <div className="absolute inset-0 bg-black/20" />
            <div className="relative space-y-2 rounded-2xl bg-black/20 px-8 py-5 backdrop-blur-sm">
              {pad.idlePromoLines.length > 0 ? (
                pad.idlePromoLines.slice(0, 3).map((line, i) => (
                  <div key={i} className={i === 0 ? 'text-xl font-bold drop-shadow' : 'text-sm opacity-90 drop-shadow'}>{line}</div>
                ))
              ) : (
                <div className="text-sm opacity-60">{t('common.noData')}</div>
              )}
            </div>
            {pad.ayceEnabled && pad.aycePackages.length > 0 && (
              <div className="absolute bottom-3 left-3 right-3 flex flex-wrap justify-center gap-2 px-2">
                <span className="self-center text-[11px] opacity-70">{t('pad.previewAyceLabel')}:</span>
                {pad.aycePackages.slice(0, 4).map(p => (
                  <span key={p.id} className="rounded-full bg-white/15 px-3 py-1 text-[11px] ring-1 ring-white/20">{p.name || '—'}</span>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="flex h-full text-gray-900">
            {/* menu grid */}
            <div className="flex-[65] overflow-hidden bg-gray-50 p-3">
              <div className="mb-2 text-xs font-semibold text-gray-500">{t('pad.previewOrdering')}</div>
              <div className="grid grid-cols-3 gap-2">
                {items.map((item, i) => (
                  <div key={i} className="rounded-lg bg-white p-2 shadow-sm">
                    <div className="mb-1.5 aspect-square w-full rounded-md bg-brand-50 flex items-center justify-center text-brand-300 text-lg font-semibold">
                      {item.name.slice(-1)}
                    </div>
                    <div className="truncate text-[11px] font-medium">{item.name}</div>
                    <div className="text-[11px] font-semibold text-brand-600">${item.price.toFixed(2)}</div>
                  </div>
                ))}
              </div>
            </div>
            {/* cart panel */}
            <div className="flex-[35] flex flex-col border-l border-gray-100 bg-white">
              <div className="border-b border-gray-100 px-3 py-2 text-xs font-semibold">{t('cds.previewTableLabel')}</div>
              <div className="flex-1 space-y-2 overflow-hidden px-3 py-2 text-[11px]">
                {cart.map((item, i) => (
                  <div key={i} className="flex items-start justify-between gap-2 border-b border-dashed border-gray-100 pb-1.5">
                    <div>
                      <div className="font-medium">{item.name} ×{item.qty}</div>
                      {item.modifier && <div className="text-[10px] text-gray-400">{item.modifier}</div>}
                    </div>
                    <div className="font-medium">${(item.qty * item.price).toFixed(2)}</div>
                  </div>
                ))}
                {pad.allowItemNotes && (
                  <div className="text-[10px] text-gray-300">{t('common.noData')}</div>
                )}
                {pad.showOrderHistory && (
                  <div className="text-[10px] text-gray-300 pt-1 border-t border-gray-100">{t('pad.showOrderHistory')}</div>
                )}
              </div>
              {pad.showRunningTotal && (
                <div className="flex items-center justify-between border-t border-gray-100 px-3 py-2 text-sm font-semibold">
                  <span className="text-xs text-gray-500">{t('cds.previewTotalLabel')}</span>
                  <span className="text-brand-600">${total.toFixed(2)}</span>
                </div>
              )}
              <div className="px-3 pb-3">
                <div className="rounded-lg bg-brand-500 py-2 text-center text-xs font-semibold text-white shadow">
                  {t('pad.previewOrdering')}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
      <p className="text-[11px] text-gray-400">{t('common.previewDisclaimer')}</p>
    </div>
  )
}
