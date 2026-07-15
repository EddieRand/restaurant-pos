import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import type { RegionConfig } from '../api/admin'
import { useRegionConfig } from '../hooks/useRegionConfig'

const PAPER_OPTIONS = [58, 80] as const
const FULFILL_MODES = ['FULL_ORDER', 'INDIVIDUAL_ITEMS'] as const
const TICKET_HEADER_MODES = ['CHECK_NUMBER', 'TABLE_NUMBER', 'CUSTOMER_NAME', 'PICKUP_NUMBER'] as const
const MODIFIER_DISPLAY_MODES = ['VERTICAL', 'HORIZONTAL'] as const
const PRINT_MODES = ['AUTO', 'ON_DEMAND'] as const

// ── small Field wrapper ─────────────────────────────────────────────
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="block text-xs font-medium text-gray-500 mb-1">{label}</span>
      {children}
    </label>
  )
}

// ── section title ────────────────────────────────────────────────────
function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h3 className="text-sm font-semibold text-gray-800">{children}</h3>
}

// ── helper: cycle through options on click ──────────────────────────

/* ==================================================================== */
/*  Page component                                                      */
/* ==================================================================== */
export default function KitchenManagementPage() {
  const { t } = useTranslation()
  const { config, loading, error, saving, saved, save, setConfig } = useRegionConfig()

  /* ---- patch helpers ---- */
  const patch = useCallback(<K extends keyof NonNullable<RegionConfig['kdsConfig']>>(
    key: K,
    value: NonNullable<RegionConfig['kdsConfig']>[K],
  ) => {
    setConfig(prev => prev && {
      ...prev,
      kdsConfig: { ...prev.kdsConfig, [key]: value },
    })
  }, [setConfig])

  const patchReceipt = useCallback(<K extends keyof NonNullable<RegionConfig['receiptConfig']>>(
    key: K,
    value: NonNullable<RegionConfig['receiptConfig']>[K],
  ) => {
    setConfig(prev => prev && {
      ...prev,
      receiptConfig: { ...prev.receiptConfig, [key]: value },
    })
  }, [setConfig])

  if (loading) return <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
  if (error) return <div className="p-8 text-sm text-red-500">加载失败: {error}</div>
  if (!config) return <div className="p-8 text-sm text-gray-400">配置未加载</div>

  const kds = config.kdsConfig
  const rcpt = config.receiptConfig

  /* ── render ─────────────────────────────────────────────────────── */
  return (
    <div className="max-w-4xl mx-auto p-8 space-y-10">
      {/* header */}
      <div>
        <h2 className="text-lg font-bold text-gray-900">{t('kitchen.title')}</h2>
        <p className="text-sm text-gray-400 mt-0.5">{t('kitchen.subtitle')}</p>
      </div>

      {/* ══════════════════  KDS 工站管理  ══════════════════ */}
      <section className="space-y-5">
        <SectionTitle>{t('kitchen.stations')}</SectionTitle>

        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">{t('kitchen.stations')}</span>
          <button
            className="btn-secondary text-xs"
            onClick={() => {
              const s = prompt(t('kitchen.addStationPrompt'), '')
              if (s) patch('stations', [...kds.stations, s])
            }}
          >+ {t('common.add')}</button>
        </div>

        <div className="space-y-2">
          {kds.stations.map((s, i) => (
            <div key={s} className="flex items-center gap-3 px-4 py-2.5 bg-gray-50 rounded-lg">
              <span className="font-mono text-xs text-gray-400 w-5">{i + 1}</span>
              <span className="flex-1 text-sm font-medium text-gray-800">{s}</span>
              <button
                className="text-gray-400 hover:text-red-500 transition-colors"
                onClick={() => {
                  const ns = [...kds.stations]; ns.splice(i, 1)
                  patch('stations', ns)
                }}
              >✕</button>
            </div>
          ))}
        </div>
      </section>

      {/* ══════════════════  KDS 显示设置  ══════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('kitchen.sectionKdsDisplay')}</SectionTitle>

        <div className="grid grid-cols-2 gap-4">
          {/* 完成模式 */}
          <Field label={t('kitchen.fulfillMode')}>
            <select
              className="input w-48 font-mono text-sm"
              value={kds.fulfillMode}
              onChange={e => patch('fulfillMode', e.target.value)}
            >
              {FULFILL_MODES.map(m => (
                <option key={m} value={m}>{t(`kitchen.fulfillMode.${m.toLowerCase()}`)}</option>
              ))}
            </select>
          </Field>

          {/* 订单头显示 */}
          <Field label={t('kitchen.ticketHeaderMode')}>
            <select
              className="input w-48 font-mono text-sm"
              value={kds.ticketHeaderMode}
              onChange={e => patch('ticketHeaderMode', e.target.value)}
            >
              {TICKET_HEADER_MODES.map(m => (
                <option key={m} value={m}>{t(`kitchen.ticketHeaderMode.${m.toLowerCase()}`)}</option>
              ))}
            </select>
          </Field>

          {/* 配料显示模式 */}
          <Field label={t('kitchen.modifierDisplayMode')}>
            <select
              className="input w-48 font-mono text-sm"
              value={kds.modifierDisplayMode}
              onChange={e => patch('modifierDisplayMode', e.target.value)}
            >
              {MODIFIER_DISPLAY_MODES.map(m => (
                <option key={m} value={m}>{t(`kitchen.modifierDisplayMode.${m.toLowerCase()}`)}</option>
              ))}
            </select>
          </Field>

          {/* 打印模式 */}
          <Field label={t('kitchen.printMode')}>
            <select
              className="input w-48 font-mono text-sm"
              value={kds.printMode}
              onChange={e => patch('printMode', e.target.value)}
            >
              {PRINT_MODES.map(m => (
                <option key={m} value={m}>{t(`kitchen.printMode.${m.toLowerCase()}`)}</option>
              ))}
            </select>
          </Field>
        </div>

        {/* 开关类设置 */}
        <div className="space-y-3 mt-4">
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input type="checkbox" className="rounded" checked={kds.showAllDayView}
              onChange={e => patch('showAllDayView', e.target.checked)} />
            {t('kitchen.showAllDayView')}
          </label>

          {kds.showAllDayView && (
            <label className="flex items-center gap-2 text-xs text-gray-400 cursor-pointer select-none ml-6">
              <input type="checkbox" className="rounded" checked={kds.allDayGroupByModifiers}
                onChange={e => patch('allDayGroupByModifiers', e.target.checked)} />
              {t('kitchen.allDayGroupByModifiers')}
            </label>
          )}

          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input type="checkbox" className="rounded" checked={kds.showOtherStationsProgress}
              onChange={e => patch('showOtherStationsProgress', e.target.checked)} />
            {t('kitchen.showOtherStationsProgress')}
          </label>

          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input type="checkbox" className="rounded" checked={kds.consolidateItems}
              onChange={e => patch('consolidateItems', e.target.checked)} />
            {t('kitchen.consolidateItems')}
          </label>

          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input type="checkbox" className="rounded" checked={kds.itemColorCodingEnabled}
              onChange={e => patch('itemColorCodingEnabled', e.target.checked)} />
            {t('kitchen.itemColorCoding')}
          </label>

          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input type="checkbox" className="rounded" checked={kds.soundAlertEnabled}
              onChange={e => patch('soundAlertEnabled', e.target.checked)} />
            {t('kitchen.soundAlert')}
          </label>

          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input type="checkbox" className="rounded" checked={kds.autoPrintFulfilled}
              onChange={e => patch('autoPrintFulfilled', e.target.checked)} />
            {t('kitchen.autoPrintFulfilled')}
          </label>
        </div>
      </section>

      {/* ══════════════════  KDS 时效设置  ══════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('kitchen.sectionKdsTiming')}</SectionTitle>

        <div className="grid grid-cols-2 gap-4">
          <Field label={t('kitchen.urgentAfter')}>
            <div className="flex items-center gap-1">
              <input
                className="input w-28 font-mono text-sm"
                type="number" min={60} max={3600}
                value={kds.urgentAfterSeconds}
                onChange={e => patch('urgentAfterSeconds', Number(e.target.value))}
              />
              <span className="text-xs text-gray-400">s</span>
            </div>
          </Field>

          <Field label={t('kitchen.criticalAfter')}>
            <div className="flex items-center gap-1">
              <input
                className="input w-28 font-mono text-sm"
                type="number" min={60} max={3600}
                value={kds.criticalAfterSeconds}
                onChange={e => patch('criticalAfterSeconds', Number(e.target.value))}
              />
              <span className="text-xs text-gray-400">s</span>
            </div>
          </Field>
        </div>
      </section>

      {/* ══════════════════  后厨打印路由  ══════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('kitchen.sectionPrinterRouting')}</SectionTitle>
        <p className="text-xs text-gray-400 -mt-3">{t('kitchen.printerRoutingHint')}</p>

        {/* 分类 → 工站 */}
        <div className="space-y-3">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">{t('kitchen.categoryRouting')}</span>
          {Object.entries(kds.categoryToStation).map(([catId, stationId]) => (
            <div key={catId} className="flex items-center gap-3">
              <span className="text-xs text-gray-500 w-32 truncate">{catId}</span>
              <select
                className="input flex-1 font-mono text-sm"
                value={stationId}
                onChange={e => {
                  const copy = { ...kds.categoryToStation, [catId]: e.target.value }
                  patch('categoryToStation', copy)
                }}
              >
                {kds.stations.map(s => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
              <button
                className="text-gray-400 hover:text-red-500 text-xs"
                onClick={() => {
                  const copy = { ...kds.categoryToStation }
                  delete copy[catId]
                  patch('categoryToStation', copy)
                }}
              >✕</button>
            </div>
          ))}

          <button
            className="btn-secondary text-xs"
            onClick={() => {
              if (kds.stations.length === 0) {
                alert(t('kitchen.addStationFirst'))
                return
              }
              const catId = prompt(t('kitchen.addRoutingPrompt'), '')
              if (!catId) return
              const station = kds.stations[0]
              patch('categoryToStation', { ...kds.categoryToStation, [catId]: station })
            }}
          >+ {t('kitchen.addCategoryRouting')}</button>
        </div>

        {/* 订单类型 → 工站（对标 Square 订单类型路由）*/}
        <div className="space-y-3 mt-6">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">{t('kitchen.orderTypeRouting')}</span>
          {Object.entries(kds.orderTypeToStation ?? {}).map(([orderType, stationId]) => (
            <div key={orderType} className="flex items-center gap-3">
              <span className="text-xs text-gray-500 w-32 truncate">{orderType}</span>
              <select
                className="input flex-1 font-mono text-sm"
                value={stationId}
                onChange={e => {
                  const copy = { ...(kds.orderTypeToStation ?? {}), [orderType]: e.target.value }
                  patch('orderTypeToStation', copy)
                }}
              >
                {kds.stations.map(s => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
              <button
                className="text-gray-400 hover:text-red-500 text-xs"
                onClick={() => {
                  const copy = { ...(kds.orderTypeToStation ?? {}) }
                  delete copy[orderType]
                  patch('orderTypeToStation', copy)
                }}
              >✕</button>
            </div>
          ))}

          <button
            className="btn-secondary text-xs"
            onClick={() => {
              const orderType = prompt(t('kitchen.addOrderTypeRoutingPrompt'), 'DINE_IN')
              if (!orderType) return
              const station = kds.stations[0] ?? ''
              patch('orderTypeToStation', { ...(kds.orderTypeToStation ?? {}), [orderType]: station })
            }}
          >+ {t('kitchen.addOrderTypeRouting')}</button>
        </div>
      </section>

      {/* ══════════════════  厨房打印机硬件  ══════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('kitchen.sectionPrinterHardware')}</SectionTitle>

        <div className="grid grid-cols-2 gap-4">
          <Field label={t('kitchen.expediterPrinter')}>
            <input
              className="input w-48 font-mono text-sm"
              type="text"
              placeholder={t('kitchen.expediterPrinterHint')}
              value={kds.expediterPrinterId ?? ''}
              onChange={e => patch('expediterPrinterId', e.target.value || null)}
            />
          </Field>

          <Field label={t('kitchen.paperWidth')}>
            <select
              className="input w-36 font-mono text-sm"
              value={rcpt.paperWidthMm}
              onChange={e => patchReceipt('paperWidthMm', Number(e.target.value))}
            >
              {PAPER_OPTIONS.map(w => (
                <option key={w} value={w}>{w}mm</option>
              ))}
            </select>
          </Field>

          <Field label={t('kitchen.printDensity')}>
            <input
              className="input w-24 font-mono text-sm"
              type="number" min={0} max={100}
              value={rcpt.printDensity}
              onChange={e => patchReceipt('printDensity', Number(e.target.value))}
            />
          </Field>

          <Field label={t('kitchen.fontSizeScale')}>
            <input
              className="input w-24 font-mono text-sm"
              type="number" step={0.1} min={0.5} max={2}
              value={rcpt.fontSizeScale}
              onChange={e => patchReceipt('fontSizeScale', Number(e.target.value))}
            />
          </Field>
        </div>

        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input type="checkbox" className="rounded" checked={rcpt.printLogo}
              onChange={e => patchReceipt('printLogo', e.target.checked)} />
            {t('kitchen.printLogo')}
          </label>
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input type="checkbox" className="rounded" checked={rcpt.kitchenTicketPerOrder}
              onChange={e => patchReceipt('kitchenTicketPerOrder', e.target.checked)} />
            {t('kitchen.kitchenTicketPerOrder')}
          </label>
        </div>
      </section>

      {/* save */}
      <div className="flex items-center gap-3 pt-4 border-t border-gray-100">
        <button className="btn-primary" disabled={saving} onClick={save}>
          {saving ? t('common.saving') : t('common.save')}
        </button>
        {saved && <span className="text-xs text-emerald-600">{t('common.saved')}</span>}
      </div>
    </div>
  )
}
