import { useTranslation } from 'react-i18next'
import type { ReceiptConfig } from '../api/admin'

export function ReceiptPreview({ receipt }: { receipt: ReceiptConfig }) {
  const { t } = useTranslation()

  const items = [
    { name: `${t('cds.previewSampleItem')} A`, qty: 2, price: 12.0 },
    { name: `${t('cds.previewSampleItem')} B`, qty: 1, price: 8.5 },
  ]
  const total = items.reduce((sum, i) => sum + i.qty * i.price, 0)

  const widthPx = Math.round((receipt.paperWidthMm / 58) * 220)
  const fontScale = receipt.fontSizeScale || 1

  return (
    <div className="lg:sticky lg:top-8 space-y-3">
      <div className="flex justify-center rounded-xl border border-gray-200 bg-gray-50 p-4">
        <div
          className="bg-white px-3 py-3 font-mono text-gray-900 shadow-sm"
          style={{ width: `${widthPx}px`, fontSize: `${11 * fontScale}px`, lineHeight: 1.4 }}
        >
          {receipt.printLogo && (
            <div className="mb-1 flex justify-center">
              <div className="h-8 w-8 rounded bg-gray-200" />
            </div>
          )}
          {receipt.headerLines.filter(Boolean).map((line, i) => (
            <div key={i} className="text-center">{line}</div>
          ))}
          <div className="my-1.5 border-t border-dashed border-gray-300" />
          {items.map((item, i) => (
            <div key={i} className="flex justify-between gap-2">
              <span>{item.name} ×{item.qty}</span>
              <span>{(item.qty * item.price).toFixed(2)}</span>
            </div>
          ))}
          <div className="my-1.5 border-t border-dashed border-gray-300" />
          <div className="flex justify-between font-bold">
            <span>{t('cds.previewTotalLabel')}</span>
            <span>{total.toFixed(2)}</span>
          </div>
          {receipt.showTaxId && receipt.taxId && (
            <div className="mt-1.5 text-center">{t('settings.taxId')}: {receipt.taxId}</div>
          )}
          {receipt.footerLines.filter(Boolean).length > 0 && (
            <div className="mt-1.5 border-t border-dashed border-gray-300 pt-1.5">
              {receipt.footerLines.filter(Boolean).map((line, i) => (
                <div key={i} className="text-center">{line}</div>
              ))}
            </div>
          )}
        </div>
      </div>
      <p className="text-[11px] text-gray-400">{t('common.previewDisclaimer')}</p>
    </div>
  )
}
