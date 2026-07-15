import { useTranslation } from 'react-i18next'
import type { QrOrderingConfig } from '../api/admin'

export function QrMenuPreview({ appearance }: { appearance: QrOrderingConfig['appearance'] }) {
  const { t } = useTranslation()

  const items = [
    { name: `${t('cds.previewSampleItem')} A`, price: 12.0, modifier: t('cds.previewModifier') },
    { name: `${t('cds.previewSampleItem')} B`, price: 8.5, modifier: null },
    { name: `${t('cds.previewSampleItem')} C`, price: 6.0, modifier: null },
    { name: `${t('cds.previewSampleItem')} D`, price: 15.0, modifier: null },
  ]

  return (
    <div className="lg:sticky lg:top-8 space-y-3">
      <div className="mx-auto aspect-[9/16] w-full max-w-[280px] overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
        <div className="flex h-full flex-col">
          <div
            className="h-28 shrink-0 bg-cover bg-center"
            style={{ backgroundImage: `url(${appearance.bannerImageUrl || '/previews/qr-menu-banner.svg'})` }}
          />
          <div className="px-3 py-2 text-sm font-semibold text-white" style={{ backgroundColor: appearance.themeColor }}>
            {t('qrOrdering.previewMenuTitle')}
          </div>
          <div className="flex-1 overflow-hidden bg-gray-50 p-2.5">
            {appearance.menuCardStyle === 'grid' ? (
              <div className="grid grid-cols-2 gap-2">
                {items.map((item, i) => (
                  <div key={i} className="rounded-lg bg-white p-2 shadow-sm">
                    <div className="mb-1.5 aspect-square w-full rounded-md bg-brand-50 flex items-center justify-center text-brand-300 text-base font-semibold">
                      {item.name.slice(-1)}
                    </div>
                    <div className="truncate text-[11px] font-medium">{item.name}</div>
                    {item.modifier && <div className="truncate text-[10px] text-gray-400">{item.modifier}</div>}
                    <div className="mt-1 flex items-center justify-between">
                      <span className="text-[11px] font-semibold" style={{ color: appearance.themeColor }}>${item.price.toFixed(2)}</span>
                      <span
                        className="rounded-full px-2 py-0.5 text-[10px] font-semibold text-white"
                        style={{ backgroundColor: appearance.themeColor }}
                      >
                        {t('qrOrdering.addToCart')}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="space-y-2">
                {items.map((item, i) => (
                  <div key={i} className="flex items-center gap-2.5 rounded-lg bg-white p-2 shadow-sm">
                    <div className="h-10 w-10 shrink-0 rounded-md bg-brand-50 flex items-center justify-center text-brand-300 text-sm font-semibold">
                      {item.name.slice(-1)}
                    </div>
                    <div className="flex-1 overflow-hidden">
                      <div className="truncate text-[11px] font-medium">{item.name}</div>
                      {item.modifier && <div className="truncate text-[10px] text-gray-400">{item.modifier}</div>}
                      <div className="text-[11px] font-semibold" style={{ color: appearance.themeColor }}>${item.price.toFixed(2)}</div>
                    </div>
                    <span
                      className="rounded-full px-2 py-0.5 text-[10px] font-semibold text-white shrink-0"
                      style={{ backgroundColor: appearance.themeColor }}
                    >
                      {t('qrOrdering.addToCart')}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
      <p className="text-[11px] text-gray-400">{t('common.previewDisclaimer')}</p>
    </div>
  )
}
