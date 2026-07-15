import { useEffect, useState, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import i18n from '../i18n'
import { type RegionConfig, type TaxRate, type ReceiptConfig, type PaymentMethodConfig, paymentMethodApi } from '../api/admin'
import { useRegionConfig } from '../hooks/useRegionConfig'
import { ReceiptPreview } from '../components/ReceiptPreview'

// ── Shared portal dropdown helper ─────────────────────────────────────────────
interface DropdownPos { top: number; left: number; width: number; openUp: boolean }

function calcPos(btn: HTMLElement): DropdownPos {
  const r = btn.getBoundingClientRect()
  const openUp = window.innerHeight - r.bottom < 320
  return { top: openUp ? r.top : r.bottom + 4, left: r.left, width: r.width, openUp }
}

// ── Timezone helpers ──────────────────────────────────────────────────────────
function tzOffset(tz: string): string {
  try {
    const p = new Intl.DateTimeFormat('en', { timeZone: tz, timeZoneName: 'shortOffset' })
      .formatToParts(new Date())
    return p.find(x => x.type === 'timeZoneName')?.value ?? ''
  } catch { return '' }
}

function tzLabel(tz: string, locale: string): string {
  try {
    const p = new Intl.DateTimeFormat(locale, { timeZone: tz, timeZoneName: 'long' })
      .formatToParts(new Date())
    return p.find(x => x.type === 'timeZoneName')?.value ?? tz
  } catch { return tz }
}


type Tab = 'currency' | 'tax' | 'payments' | 'receipt' | 'terminal'

export default function SettingsPage() {
  const { t } = useTranslation()
  const { config, loading, saving, saved, error, save, setConfig } = useRegionConfig()
  const [tab, setTab] = useState<Tab>('currency')

  function updateConfig(patch: Partial<RegionConfig>) {
    if (!config) return
    if (patch.locale && patch.locale !== config.locale) {
      i18n.changeLanguage(patch.locale)
      localStorage.setItem('pos_locale', patch.locale)
    }
    setConfig(prev => prev ? { ...prev, ...patch } : prev)
  }

  function updateReceipt(patch: Partial<ReceiptConfig>) {
    if (!config) return
    setConfig(prev => prev ? { ...prev, receiptConfig: { ...prev.receiptConfig, ...patch } } : prev)
  }

  function addTaxRate() {
    if (!config) return
    const newRate: TaxRate = { id: crypto.randomUUID(), name: '新税率', ratePermille: 80, mode: 'INCLUSIVE', rounding: 'HALF_UP' }
    updateConfig({ availableTaxRates: [...config.availableTaxRates, newRate] })
  }

  function removeTaxRate(id: string) {
    if (!config) return
    updateConfig({ availableTaxRates: config.availableTaxRates.filter(t => t.id !== id) })
  }

  function updateTaxRate(id: string, patch: Partial<TaxRate>) {
    if (!config) return
    updateConfig({ availableTaxRates: config.availableTaxRates.map(t => t.id === id ? { ...t, ...patch } : t) })
  }

  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodConfig[]>([])

  function loadPaymentMethods() {
    paymentMethodApi.list().then(d => setPaymentMethods(Array.isArray(d) ? d : [])).catch(() => {})
  }

  useEffect(() => { loadPaymentMethods() }, [])

  async function addPaymentMethod() {
    const code = `CUSTOM_${Date.now()}`
    await paymentMethodApi.create({ code, baseType: 'OTHER', displayName: '新支付方式', color: 'gray', sortOrder: (paymentMethods.length + 1) * 10, isActive: true })
    loadPaymentMethods()
  }

  async function updatePaymentMethod(id: string, patch: Partial<Omit<PaymentMethodConfig, 'id' | 'code' | 'baseType'>>) {
    setPaymentMethods(prev => prev.map(p => p.id === id ? { ...p, ...patch } : p))
    await paymentMethodApi.update(id, patch)
  }

  async function removePaymentMethod(id: string) {
    if (!confirm(t('settings.paymentMethodDeleteConfirm'))) return
    await paymentMethodApi.delete(id)
    loadPaymentMethods()
  }

  const [dragIndex, setDragIndex] = useState<number | null>(null)
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null)

  async function reorderPaymentMethods(from: number | null, to: number) {
    if (from === null || from === to) return
    const reordered = [...paymentMethods]
    const [moved] = reordered.splice(from, 1)
    reordered.splice(to, 0, moved)
    const withSortOrder = reordered.map((pm, i) => ({ ...pm, sortOrder: (i + 1) * 10 }))
    setPaymentMethods(withSortOrder)
    await Promise.all(withSortOrder.map(pm => paymentMethodApi.update(pm.id, { sortOrder: pm.sortOrder })))
  }

  if (loading) {
    return <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
  }
  if (!config) {
    return <div className="p-8 text-sm text-gray-400">{t('common.configNotLoaded')}</div>
  }

  return (
    <div className="p-8 w-full">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">{t('settings.title')}</h1>
          <p className="mt-1 text-sm text-gray-500">{t('settings.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          {saved && (
            <span className="flex items-center gap-1.5 text-sm text-green-600">
              <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
              </svg>
              {t('settings.saved')}
            </span>
          )}
          <button className="btn-primary" onClick={save} disabled={saving}>
            {saving ? t('settings.saving') : t('settings.save')}
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-100 text-sm text-red-700">{error}</div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 mb-6 bg-gray-100 p-1 rounded-lg w-fit">
        {([['currency', t('settings.tabCurrency')], ['tax', t('settings.tabTax')], ['payments', t('settings.tabPayments')], ['receipt', t('settings.tabReceipt')], ['terminal', t('settings.tabTerminal')]] as [Tab, string][]).map(
          ([key, label]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`px-4 py-1.5 rounded-md text-xs font-medium transition-colors whitespace-nowrap ${
                tab === key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {label}
            </button>
          )
        )}
      </div>

      <div className={tab === 'receipt' ? 'grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-8 items-start' : ''}>
      <div className="card p-6 space-y-5">
        {/* ── Currency & Region ── */}
        {tab === 'currency' && (
          <>
            {/* Country preset selector */}
            <div className="bg-brand-50 border border-brand-100 rounded-xl p-4 flex items-start gap-4">
              <div className="flex-1">
                <p className="text-sm font-semibold text-brand-700 mb-1">{t('settings.countryRegion')}</p>
                <p className="text-xs text-brand-500 mb-3">{t('settings.countryRegionHint')}</p>
                <CountrySelect
                  value={config.country ?? ''}
                  onChange={code => {
                    const p = COUNTRY_PRESETS[code]
                    if (!p) return
                    updateConfig({
                      country: code,
                      currencyCode: p.currencyCode,
                      currencySymbol: p.currencySymbol,
                      currencyMinorDigits: p.minorDigits,
                      decimalSeparator: p.decimalSeparator,
                      thousandsSeparator: p.groupingSeparator,
                      locale: p.locale,
                      menuPrimaryLanguage: p.menuPrimaryLanguage,
                      timeZone: p.timeZone,
                      availableTaxRates: p.taxRates.map(rate => ({
                        id: rate.id,
                        name: rate.name,
                        ratePermille: rate.ratePermille,
                        mode: rate.isInclusive ? 'INCLUSIVE' as const : 'EXCLUSIVE' as const,
                        rounding: 'HALF_UP' as const,
                      })),
                      serviceChargePermille: p.serviceChargePermille,
                    })
                  }}
                />
              </div>
            </div>

            <SectionTitle>{t('settings.sectionCurrency')}</SectionTitle>
            <div className="grid grid-cols-2 gap-4">
              <Field label={t('settings.currencyCode')}>
                <CurrencySelect value={config.currencyCode}
                  onChange={v => updateConfig({ currencyCode: v, currencySymbol: defaultSymbol(v) })} />
              </Field>
              <Field label={t('settings.currencySymbol')}>
                <CurrencySymbolField
                  currencyCode={config.currencyCode}
                  value={config.currencySymbol}
                  onChange={v => updateConfig({ currencySymbol: v })} />
              </Field>
              <Field label={t('settings.minorDigits')}>
                <select className="input w-32 font-mono" value={config.currencyMinorDigits}
                  onChange={e => updateConfig({ currencyMinorDigits: Number(e.target.value) })}>
                  <option value={0}>0 — 1,234</option>
                  <option value={1}>1 — 1,234.5</option>
                  <option value={2}>2 — 1,234.56</option>
                  <option value={3}>3 — 1,234.567</option>
                </select>
              </Field>
              {config.currencyMinorDigits > 0 && (
                <Field label={t('settings.decimalSep')}>
                  <select className="input w-40 font-mono" value={config.decimalSeparator}
                    onChange={e => updateConfig({ decimalSeparator: e.target.value })}>
                    <option value=".">. （英式 1.23）</option>
                    <option value=",">, （欧式 1,23）</option>
                    <option value="·">· （中点 1·23）</option>
                  </select>
                </Field>
              )}
              <Field label={t('settings.groupSep')}>
                <select className="input w-48 font-mono" value={config.thousandsSeparator}
                  onChange={e => updateConfig({ thousandsSeparator: e.target.value })}>
                  <option value=",">, （逗号 1,234）</option>
                  <option value=".">. （句点 1.234）</option>
                  <option value=" ">  （空格 1 234）</option>
                  <option value="'">' （撇号 1'234）</option>
                  <option value="">（无 1234）</option>
                </select>
              </Field>
            </div>

            <div className="border-t border-gray-100 pt-5">
              <SectionTitle>{t('settings.sectionRegion')}</SectionTitle>
              <div className="grid grid-cols-2 gap-4 mt-4">
                <Field label={t('settings.timezone')}>
                  <TimezoneSelect value={config.timeZone} onChange={v => updateConfig({ timeZone: v })} />
                </Field>
                <Field label={t('settings.language')}>
                  <LocaleSelect value={config.locale} onChange={v => updateConfig({ locale: v })} />
                </Field>
                <Field label={t('settings.menuPrimaryLanguage')} hint={t('settings.menuPrimaryLanguageHint')}>
                  <MenuLanguageSelect value={config.menuPrimaryLanguage} onChange={v => updateConfig({ menuPrimaryLanguage: v })} />
                </Field>
              </div>
            </div>
          </>
        )}

        {/* ── Tax & Service Charge ── */}
        {tab === 'tax' && (
          <>
            <div className="flex items-center justify-between">
              <SectionTitle>{t('settings.sectionTaxRates')}</SectionTitle>
              <button className="btn-secondary text-xs" onClick={addTaxRate}>{t('settings.addTaxRate')}</button>
            </div>

            {config.availableTaxRates.length === 0 && (
              <p className="text-sm text-gray-400 py-4 text-center">{t('settings.noTaxRates')}</p>
            )}

            <div className="space-y-3">
              {config.availableTaxRates.map(rate => (
                <div key={rate.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                  <input
                    className="input flex-1"
                    value={rate.name}
                    onChange={e => updateTaxRate(rate.id, { name: e.target.value })}
                    placeholder={t('settings.taxRateName')}
                  />
                  <div className="flex items-center gap-1.5">
                    <input
                      className="input w-20 font-mono text-right"
                      type="number" min={0} max={1000} step={1}
                      value={rate.ratePermille}
                      onChange={e => updateTaxRate(rate.id, { ratePermille: Number(e.target.value) })}
                    />
                    <span className="text-xs text-gray-500 w-12">
                      = {(rate.ratePermille / 10).toFixed(1)}%
                    </span>
                  </div>
                  <label className="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      className="rounded"
                      checked={rate.mode === 'INCLUSIVE'}
                      onChange={e => updateTaxRate(rate.id, { mode: e.target.checked ? 'INCLUSIVE' : 'EXCLUSIVE' })}
                    />
                    {t('settings.inclusive')}
                  </label>
                  <button
                    className="text-gray-400 hover:text-red-500 transition-colors"
                    onClick={() => removeTaxRate(rate.id)}
                  >
                    <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>

            <div className="border-t border-gray-100 pt-5">
              <SectionTitle>{t('settings.sectionServiceCharge')}</SectionTitle>
              <div className="flex items-center gap-3 mt-4">
                <Field label={t('settings.serviceChargeRate')}>
                  <div className="flex items-center gap-2">
                    <input
                      className="input w-24 font-mono"
                      type="number" min={0} max={1000} step={1}
                      value={config.serviceChargePermille}
                      onChange={e => updateConfig({ serviceChargePermille: Number(e.target.value) })}
                    />
                    <span className="text-sm text-gray-500">
                      = {(config.serviceChargePermille / 10).toFixed(1)}%
                    </span>
                  </div>
                </Field>
              </div>

              {/* ── Tip Settings ── */}
              <SectionTitle>{t('settings.tipSettingsTitle')}</SectionTitle>
              <div className="space-y-4 mt-4">
                {/* Global enable toggle */}
                <Field label={t('settings.tipEnabled')}>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      className="w-4 h-4 accent-brand-500"
                      checked={config.tipConfig.enabled}
                      onChange={e => updateConfig({ tipConfig: { ...config.tipConfig, enabled: e.target.checked } })}
                    />
                    <span className="text-sm text-gray-600">{t('settings.tipEnabled')}</span>
                  </label>
                </Field>

                {config.tipConfig.enabled && (
                  <>
                    {/* Preset percentages */}
                    <Field label={t('settings.tipPresets')}>
                      <div className="flex flex-wrap gap-2">
                        {[5, 10, 15, 18, 20, 22, 25].map(pct => {
                          const active = config.tipConfig.presets.includes(pct)
                          return (
                            <button
                              key={pct}
                              type="button"
                              onClick={() => {
                                const next = active
                                  ? config.tipConfig.presets.filter(p => p !== pct)
                                  : [...config.tipConfig.presets, pct].sort((a, b) => a - b)
                                updateConfig({ tipConfig: { ...config.tipConfig, presets: next } })
                              }}
                              className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                                active
                                  ? 'bg-brand-500 text-white border-brand-500'
                                  : 'bg-white text-gray-600 border-gray-200 hover:border-brand-300'
                              }`}
                            >
                              {pct}%
                            </button>
                          )
                        })}
                      </div>
                    </Field>

                    {/* Calc base */}
                    <Field label={t('settings.tipCalcBase')}>
                      <div className="flex gap-4">
                        {(['SUBTOTAL', 'POST_TAX'] as const).map(base => (
                          <label key={base} className="flex items-center gap-1.5 cursor-pointer text-sm text-gray-700">
                            <input
                              type="radio"
                              name="tipCalcBase"
                              value={base}
                              checked={config.tipConfig.calcBase === base}
                              onChange={() => updateConfig({ tipConfig: { ...config.tipConfig, calcBase: base } })}
                              className="accent-brand-500"
                            />
                            {base === 'SUBTOTAL' ? t('settings.tipCalcSubtotal') : t('settings.tipCalcPostTax')}
                          </label>
                        ))}
                      </div>
                    </Field>

                    {/* Per-channel toggles */}
                    <Field label={t('settings.tipOnQr')}>
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input
                          type="checkbox"
                          className="w-4 h-4 accent-brand-500"
                          checked={config.tipConfig.enabledOnQr}
                          onChange={e => updateConfig({ tipConfig: { ...config.tipConfig, enabledOnQr: e.target.checked } })}
                        />
                        <span className="text-sm text-gray-600">{t('settings.tipOnQr')}</span>
                      </label>
                    </Field>
                    <Field label={t('settings.tipOnKiosk')}>
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input
                          type="checkbox"
                          className="w-4 h-4 accent-brand-500"
                          checked={config.tipConfig.enabledOnKiosk}
                          onChange={e => updateConfig({ tipConfig: { ...config.tipConfig, enabledOnKiosk: e.target.checked } })}
                        />
                        <span className="text-sm text-gray-600">{t('settings.tipOnKiosk')}</span>
                      </label>
                    </Field>
                    <Field label={t('settings.tipOnPad')}>
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input
                          type="checkbox"
                          className="w-4 h-4 accent-brand-500"
                          checked={config.tipConfig.enabledOnPad}
                          onChange={e => updateConfig({ tipConfig: { ...config.tipConfig, enabledOnPad: e.target.checked } })}
                        />
                        <span className="text-sm text-gray-600">{t('settings.tipOnPad')}</span>
                      </label>
                    </Field>
                  </>
                )}
              </div>
            </div>

            {/* ── Timeclock ── */}
            <SectionTitle>{t('settings.timeclockSettingsTitle')}</SectionTitle>
            <div className="bg-white rounded-2xl border border-gray-100 p-5 space-y-4">
              <Field label={t('settings.timeclockEnabled')}>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    className="w-4 h-4 accent-brand-500"
                    checked={config.timeclockConfig.enabled}
                    onChange={e => updateConfig({ timeclockConfig: { ...config.timeclockConfig, enabled: e.target.checked } })}
                  />
                  <span className="text-sm text-gray-600">{t('settings.timeclockEnabled')}</span>
                </label>
              </Field>
              {config.timeclockConfig.enabled && (
                <Field label={t('settings.timeclockRequireForOrders')}>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      className="w-4 h-4 accent-brand-500"
                      checked={config.timeclockConfig.requireClockInForOrders}
                      onChange={e => updateConfig({ timeclockConfig: { ...config.timeclockConfig, requireClockInForOrders: e.target.checked } })}
                    />
                    <span className="text-sm text-gray-600">{t('settings.timeclockRequireForOrders')}</span>
                  </label>
                </Field>
              )}
            </div>
          </>
        )}

        {/* ── Payment Methods ── */}
        {tab === 'payments' && (
          <>
            <div className="flex items-center justify-between">
              <SectionTitle>{t('settings.paymentMethodsTitle')}</SectionTitle>
              <button className="btn-secondary text-xs" onClick={addPaymentMethod}>{t('settings.paymentMethodAdd')}</button>
            </div>
            <p className="text-xs text-gray-400">{t('settings.paymentMethodsHint')}</p>

            <div className="flex items-center gap-3 px-3 mt-4 text-xs text-gray-400">
              <span className="w-4"></span>
              <span className="flex-1">{t('settings.paymentMethodName')}</span>
              <span className="w-[3.75rem]"></span>
              <span className="w-4"></span>
            </div>

            <div className="space-y-3 mt-1">
              {paymentMethods.map((pm, idx) => {
                const isCash = pm.code === 'CASH'
                return (
                  <div
                    key={pm.id}
                    className={`flex items-center gap-3 p-3 bg-gray-50 rounded-lg ${dragOverIndex === idx ? 'ring-2 ring-brand-300' : ''}`}
                    draggable
                    onDragStart={() => setDragIndex(idx)}
                    onDragOver={e => { e.preventDefault(); if (dragOverIndex !== idx) setDragOverIndex(idx) }}
                    onDragLeave={() => setDragOverIndex(prev => prev === idx ? null : prev)}
                    onDrop={e => { e.preventDefault(); reorderPaymentMethods(dragIndex, idx); setDragIndex(null); setDragOverIndex(null) }}
                    onDragEnd={() => { setDragIndex(null); setDragOverIndex(null) }}
                  >
                    <span className="text-gray-300 cursor-grab active:cursor-grabbing select-none w-4 text-center" title={t('settings.paymentMethodDragHint')}>⠿</span>
                    <input
                      className="input flex-1"
                      value={pm.displayName}
                      placeholder={t('settings.paymentMethodName')}
                      onChange={e => updatePaymentMethod(pm.id, { displayName: e.target.value })}
                    />
                    <label className={`flex items-center gap-1.5 text-xs text-gray-600 select-none ${isCash ? 'opacity-50' : 'cursor-pointer'}`}>
                      <input
                        type="checkbox"
                        className="rounded"
                        checked={pm.isActive}
                        disabled={isCash}
                        onChange={e => updatePaymentMethod(pm.id, { isActive: e.target.checked })}
                      />
                      {t('settings.paymentMethodActive')}
                    </label>
                    <button
                      className={`text-gray-400 transition-colors ${isCash ? 'opacity-30 cursor-not-allowed' : 'hover:text-red-500'}`}
                      disabled={isCash}
                      onClick={() => !isCash && removePaymentMethod(pm.id)}
                    >
                      <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                      </svg>
                    </button>
                  </div>
                )
              })}
            </div>
          </>
        )}

        {/* ── Receipt ── */}
        {tab === 'receipt' && (
          <>
            <SectionTitle>{t('settings.sectionReceiptHeader')}</SectionTitle>
            <Field label={t('settings.receiptHeaderLines')}>
              <textarea
                className="input h-24 resize-none font-mono text-xs"
                value={config.receiptConfig.headerLines.join('\n')}
                onChange={e => updateReceipt({ headerLines: e.target.value.split('\n') })}
                placeholder={t('settings.receiptHeaderPlaceholder')}
              />
            </Field>

            <div className="border-t border-gray-100 pt-5">
              <SectionTitle>{t('settings.sectionReceiptFooter')}</SectionTitle>
              <Field label={t('settings.receiptFooterLines')}>
                <textarea
                  className="input h-20 resize-none font-mono text-xs"
                  value={config.receiptConfig.footerLines.join('\n')}
                  onChange={e => updateReceipt({ footerLines: e.target.value.split('\n') })}
                  placeholder={t('settings.receiptFooterPlaceholder')}
                />
              </Field>
            </div>

            <div className="border-t border-gray-100 pt-5">
              <SectionTitle>{t('settings.sectionTaxId')}</SectionTitle>
              <label className="flex items-center gap-2.5 cursor-pointer select-none mt-3">
                <input
                  type="checkbox"
                  className="rounded"
                  checked={config.receiptConfig.showTaxId}
                  onChange={e => updateReceipt({ showTaxId: e.target.checked })}
                />
                <span className="text-sm text-gray-700">{t('settings.showTaxId')}</span>
              </label>
              {config.receiptConfig.showTaxId && (
                <div className="mt-3">
                  <Field label={t('settings.taxId')}>
                    <input
                      className="input font-mono"
                      value={config.receiptConfig.taxId}
                      onChange={e => updateReceipt({ taxId: e.target.value })}
                      placeholder={t('settings.taxIdPlaceholder')}
                    />
                  </Field>
                </div>
              )}
            </div>
          </>
        )}

        {/* ── Terminal ── */}
        {tab === 'terminal' && (
          <>
            <SectionTitle>{t('settings.sectionTerminal')}</SectionTitle>
            <Field label={t('settings.terminalId')}>
              <input
                className="input font-mono"
                value={config.terminalId}
                onChange={e => updateConfig({ terminalId: e.target.value })}
                placeholder="pos-1"
              />
            </Field>
            <p className="text-xs text-gray-400">
              {t('settings.terminalIdHelp')}
            </p>

            {/* Preview */}
            <div className="border-t border-gray-100 pt-5">
              <SectionTitle>{t('settings.configPreview')}</SectionTitle>
              <pre className="mt-3 p-4 bg-gray-50 rounded-lg text-xs font-mono text-gray-600 overflow-x-auto whitespace-pre-wrap break-all">
                {JSON.stringify(config, null, 2)}
              </pre>
            </div>
          </>
        )}
      </div>

      {tab === 'receipt' && <ReceiptPreview receipt={config.receiptConfig} />}
      </div>
    </div>
  )
}

// ── Small helpers ─────────────────────────────────────────────────────────────

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h3 className="text-sm font-semibold text-gray-800">{children}</h3>
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-500 mb-1.5">{label}</label>
      {children}
      {hint && <p className="mt-1 text-xs text-gray-400">{hint}</p>}
    </div>
  )
}

const LOCALES = [
  { value: 'zh-CN', flag: '🇨🇳', label: '简体中文', code: 'zh-CN' },
  { value: 'zh-TW', flag: '🇹🇼', label: '繁體中文', code: 'zh-TW' },
  { value: 'en-US', flag: '🇺🇸', label: 'English (US)', code: 'en-US' },
  { value: 'en-GB', flag: '🇬🇧', label: 'English (UK)', code: 'en-GB' },
  { value: 'ja-JP', flag: '🇯🇵', label: '日本語', code: 'ja-JP' },
  { value: 'ko-KR', flag: '🇰🇷', label: '한국어', code: 'ko-KR' },
  { value: 'th-TH', flag: '🇹🇭', label: 'ภาษาไทย', code: 'th-TH' },
  { value: 'vi-VN', flag: '🇻🇳', label: 'Tiếng Việt', code: 'vi-VN' },
  { value: 'ms-MY', flag: '🇲🇾', label: 'Bahasa Melayu', code: 'ms-MY' },
  { value: 'ar-SA', flag: '🇸🇦', label: 'العربية', code: 'ar-SA' },
]

function LocaleSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState<DropdownPos | null>(null)
  const btnRef = useRef<HTMLButtonElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)
  const selected = LOCALES.find(l => l.value === value) ?? LOCALES[0]

  function openDropdown() {
    if (btnRef.current) setPos(calcPos(btnRef.current))
    setOpen(o => !o)
  }

  useEffect(() => {
    function onOutside(e: MouseEvent) {
      const t = e.target as Node
      if (!btnRef.current?.contains(t) && !dropRef.current?.contains(t)) setOpen(false)
    }
    document.addEventListener('mousedown', onOutside)
    return () => document.removeEventListener('mousedown', onOutside)
  }, [])

  return (
    <div>
      <button ref={btnRef} type="button" onClick={openDropdown}
        className="input flex items-center justify-between gap-2 cursor-pointer text-start w-full"
      >
        <span className="flex items-center gap-2">
          <span>{selected.flag}</span>
          <span>{selected.label}</span>
          <span className="text-gray-400 text-xs font-mono">({selected.code})</span>
        </span>
        <svg className={`w-4 h-4 text-gray-400 flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      </button>

      {open && pos && createPortal(
        <div ref={dropRef} style={{
          position: 'fixed', zIndex: 9999,
          top: pos.openUp ? undefined : pos.top,
          bottom: pos.openUp ? window.innerHeight - pos.top : undefined,
          left: pos.left, width: pos.width,
          maxHeight: '260px',
        }} className="bg-white border border-gray-200 rounded-lg shadow-xl py-1 overflow-y-auto">
          {LOCALES.map(locale => (
            <button key={locale.value} type="button"
              onClick={() => { onChange(locale.value); setOpen(false) }}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-sm hover:bg-gray-50 transition-colors ${value === locale.value ? 'bg-brand-50 text-brand-600' : 'text-gray-700'}`}
            >
              <span>{locale.flag}</span>
              <span className="flex-1 text-start">{locale.label}</span>
              <span className="text-xs font-mono text-gray-400">{locale.code}</span>
              {value === locale.value && (
                <svg className="w-3.5 h-3.5 text-brand-500 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                </svg>
              )}
            </button>
          ))}
        </div>,
        document.body
      )}
    </div>
  )
}

function MenuLanguageSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <select className="input" value={value} onChange={e => onChange(e.target.value)}>
      {LOCALES.map(l => (
        <option key={l.value} value={l.value}>{l.flag} {l.label} ({l.code})</option>
      ))}
    </select>
  )
}

// ── Country presets ───────────────────────────────────────────────────────────
function flagEmoji(code: string): string {
  return [...code.toUpperCase()].map(c => String.fromCodePoint(c.charCodeAt(0) - 65 + 0x1F1E6)).join('')
}

interface CountryPreset {
  currencyCode: string
  currencySymbol: string
  minorDigits: number
  decimalSeparator: string
  groupingSeparator: string
  locale: string
  menuPrimaryLanguage: string
  timeZone: string
  taxRates: Array<{ id: string; name: string; ratePermille: number; isInclusive: boolean }>
  serviceChargePermille: number
}

// Only countries whose locale is fully supported by the app's i18n:
// zh-CN, zh-TW, en-US, en-GB, ja-JP, ko-KR, th-TH, vi-VN, ms-MY, ar-SA
const COUNTRY_PRESETS: Record<string, CountryPreset> = {
  CN: { currencyCode:'CNY', currencySymbol:'¥',  minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'zh-CN', menuPrimaryLanguage:'zh-CN', timeZone:'Asia/Shanghai',   taxRates:[],                                                                                  serviceChargePermille:0   },
  TW: { currencyCode:'TWD', currencySymbol:'NT$', minorDigits:0, decimalSeparator:'.', groupingSeparator:',', locale:'zh-TW', menuPrimaryLanguage:'zh-TW', timeZone:'Asia/Taipei',      taxRates:[{id:'tw-vat',  name:'營業稅',              ratePermille:50,  isInclusive:true}],  serviceChargePermille:100 },
  HK: { currencyCode:'HKD', currencySymbol:'HK$', minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'zh-TW', menuPrimaryLanguage:'zh-TW', timeZone:'Asia/Hong_Kong',   taxRates:[],                                                                                  serviceChargePermille:100 },
  JP: { currencyCode:'JPY', currencySymbol:'¥',   minorDigits:0, decimalSeparator:'.', groupingSeparator:',', locale:'ja-JP', menuPrimaryLanguage:'ja-JP', timeZone:'Asia/Tokyo',       taxRates:[{id:'jp-ct',   name:'消費税',              ratePermille:100, isInclusive:true}],  serviceChargePermille:0   },
  KR: { currencyCode:'KRW', currencySymbol:'₩',   minorDigits:0, decimalSeparator:'.', groupingSeparator:',', locale:'ko-KR', menuPrimaryLanguage:'ko-KR', timeZone:'Asia/Seoul',       taxRates:[{id:'kr-vat',  name:'부가가치세',           ratePermille:100, isInclusive:true}],  serviceChargePermille:0   },
  TH: { currencyCode:'THB', currencySymbol:'฿',   minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'th-TH', menuPrimaryLanguage:'th-TH', timeZone:'Asia/Bangkok',     taxRates:[{id:'th-vat',  name:'ภาษีมูลค่าเพิ่ม',    ratePermille:70,  isInclusive:true}],  serviceChargePermille:100 },
  VN: { currencyCode:'VND', currencySymbol:'₫',   minorDigits:0, decimalSeparator:',', groupingSeparator:'.', locale:'vi-VN', menuPrimaryLanguage:'vi-VN', timeZone:'Asia/Ho_Chi_Minh', taxRates:[{id:'vn-vat',  name:'Thuế GTGT',           ratePermille:100, isInclusive:false}], serviceChargePermille:0   },
  MY: { currencyCode:'MYR', currencySymbol:'RM',  minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'ms-MY', menuPrimaryLanguage:'ms-MY', timeZone:'Asia/Kuala_Lumpur',taxRates:[{id:'my-sst',  name:'SST',                 ratePermille:60,  isInclusive:false}], serviceChargePermille:100 },
  SG: { currencyCode:'SGD', currencySymbol:'S$',  minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'ms-MY', menuPrimaryLanguage:'en-US', timeZone:'Asia/Singapore',   taxRates:[{id:'sg-gst',  name:'GST',                 ratePermille:90,  isInclusive:false}], serviceChargePermille:100 },
  SA: { currencyCode:'SAR', currencySymbol:'SAR', minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'ar-SA', menuPrimaryLanguage:'ar-SA', timeZone:'Asia/Riyadh',      taxRates:[{id:'sa-vat',  name:'ضريبة القيمة المضافة',ratePermille:150, isInclusive:false}], serviceChargePermille:0   },
  AE: { currencyCode:'AED', currencySymbol:'AED', minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'ar-SA', menuPrimaryLanguage:'ar-SA', timeZone:'Asia/Dubai',       taxRates:[{id:'ae-vat',  name:'ضريبة القيمة المضافة',ratePermille:50,  isInclusive:false}], serviceChargePermille:0   },
  QA: { currencyCode:'QAR', currencySymbol:'QAR', minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'ar-SA', menuPrimaryLanguage:'ar-SA', timeZone:'Asia/Qatar',       taxRates:[],                                                                                  serviceChargePermille:0   },
  KW: { currencyCode:'KWD', currencySymbol:'KWD', minorDigits:3, decimalSeparator:'.', groupingSeparator:',', locale:'ar-SA', menuPrimaryLanguage:'ar-SA', timeZone:'Asia/Kuwait',      taxRates:[],                                                                                  serviceChargePermille:0   },
  BH: { currencyCode:'BHD', currencySymbol:'BD',  minorDigits:3, decimalSeparator:'.', groupingSeparator:',', locale:'ar-SA', menuPrimaryLanguage:'ar-SA', timeZone:'Asia/Bahrain',     taxRates:[{id:'bh-vat',  name:'ضريبة القيمة المضافة',ratePermille:100, isInclusive:false}], serviceChargePermille:0   },
  OM: { currencyCode:'OMR', currencySymbol:'OMR', minorDigits:3, decimalSeparator:'.', groupingSeparator:',', locale:'ar-SA', menuPrimaryLanguage:'ar-SA', timeZone:'Asia/Muscat',      taxRates:[{id:'om-vat',  name:'ضريبة القيمة المضافة',ratePermille:50,  isInclusive:false}], serviceChargePermille:0   },
  US: { currencyCode:'USD', currencySymbol:'$',   minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'en-US', menuPrimaryLanguage:'en-US', timeZone:'America/New_York', taxRates:[],                                                                                  serviceChargePermille:0   },
  GB: { currencyCode:'GBP', currencySymbol:'£',   minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'en-GB', menuPrimaryLanguage:'en-GB', timeZone:'Europe/London',    taxRates:[{id:'gb-vat',  name:'VAT',                 ratePermille:200, isInclusive:true}],  serviceChargePermille:125 },
  AU: { currencyCode:'AUD', currencySymbol:'A$',  minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'en-GB', menuPrimaryLanguage:'en-AU', timeZone:'Australia/Sydney', taxRates:[{id:'au-gst',  name:'GST',                 ratePermille:100, isInclusive:true}],  serviceChargePermille:0   },
  NZ: { currencyCode:'NZD', currencySymbol:'NZ$', minorDigits:2, decimalSeparator:'.', groupingSeparator:',', locale:'en-GB', menuPrimaryLanguage:'en-NZ', timeZone:'Pacific/Auckland', taxRates:[{id:'nz-gst',  name:'GST',                 ratePermille:150, isInclusive:true}],  serviceChargePermille:0   },
}

const COUNTRY_LIST = Object.keys(COUNTRY_PRESETS)

const countryNameCache = new Map<string, string>()
function countryName(code: string, locale: string): string {
  const key = `${locale}:${code}`
  if (!countryNameCache.has(key)) {
    try {
      countryNameCache.set(key, new Intl.DisplayNames([locale], { type: 'region' }).of(code) ?? code)
    } catch { countryNameCache.set(key, code) }
  }
  return countryNameCache.get(key)!
}

function CountrySelect({ value, onChange }: { value: string; onChange: (code: string) => void }) {
  const { t, i18n: i18nInst } = useTranslation()
  const locale = i18nInst.language
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState<DropdownPos | null>(null)
  const [query, setQuery] = useState('')
  const btnRef = useRef<HTMLButtonElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    function onOutside(e: MouseEvent) {
      if (
        btnRef.current && !btnRef.current.contains(e.target as Node) &&
        dropRef.current && !dropRef.current.contains(e.target as Node)
      ) { setOpen(false); setQuery('') }
    }
    document.addEventListener('mousedown', onOutside)
    return () => document.removeEventListener('mousedown', onOutside)
  }, [])

  useEffect(() => {
    if (open && inputRef.current) setTimeout(() => inputRef.current?.focus(), 50)
  }, [open])

  function handleOpen() {
    if (!open && btnRef.current) setPos(calcPos(btnRef.current))
    setOpen(o => !o)
  }

  const q = query.toLowerCase()
  const filtered = q
    ? COUNTRY_LIST.filter(c => c.toLowerCase().includes(q) || countryName(c, locale).toLowerCase().includes(q))
    : COUNTRY_LIST

  const dropStyle: React.CSSProperties = pos ? {
    position: 'fixed', zIndex: 9999, left: pos.left, width: pos.width,
    ...(pos.openUp ? { bottom: window.innerHeight - pos.top } : { top: pos.top }),
    maxHeight: '320px', display: 'flex', flexDirection: 'column',
  } : {}

  const current = value ? COUNTRY_PRESETS[value] : null

  return (
    <div className="relative">
      <button ref={btnRef} type="button" onClick={handleOpen}
        className="input flex items-center gap-3 cursor-pointer text-start w-full">
        {value ? (
          <>
            <span className="text-2xl leading-none">{flagEmoji(value)}</span>
            <span className="flex-1">{countryName(value, locale)}</span>
            {current && <span className="text-xs text-gray-400 font-mono flex-shrink-0">{current.currencyCode}</span>}
          </>
        ) : (
          <span className="text-gray-400">{t('settings.countryPlaceholder')}</span>
        )}
        <svg className={`w-4 h-4 text-gray-400 flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      </button>
      {open && pos && createPortal(
        <div ref={dropRef} style={dropStyle} className="bg-white border border-gray-200 rounded-lg shadow-xl">
          <div className="p-2 border-b border-gray-100 flex-shrink-0">
            <input ref={inputRef} className="input text-sm py-1.5 w-full"
              placeholder={t('settings.countryPlaceholder')} value={query}
              onChange={e => setQuery(e.target.value)}
              onClick={e => e.stopPropagation()} />
          </div>
          <div className="overflow-y-auto flex-1">
            {filtered.map(c => {
              const preset = COUNTRY_PRESETS[c]
              return (
                <button key={c} type="button"
                  onClick={() => { onChange(c); setOpen(false); setQuery('') }}
                  className={`w-full text-start px-3 py-2.5 text-sm hover:bg-gray-50 transition-colors flex items-center gap-3 ${c === value ? 'bg-brand-50 text-brand-600 font-semibold' : 'text-gray-700'}`}
                >
                  <span className="text-xl leading-none">{flagEmoji(c)}</span>
                  <span className="flex-1">{countryName(c, locale)}</span>
                  <span className="text-xs text-gray-400 font-mono">{preset.currencyCode}</span>
                </button>
              )
            })}
            {filtered.length === 0 && <p className="px-3 py-4 text-sm text-gray-400 text-center">—</p>}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}

// ── Currency data ─────────────────────────────────────────────────────────────

// Currencies with multiple common symbol variants (code first = default)
const MULTI_SYMBOLS: Record<string, string[]> = {
  AED: ['AED', 'د.إ', 'Dhs'],
  SAR: ['SAR', '﷼', 'ر.س'],
  QAR: ['QAR', '﷼', 'ر.ق'],
  OMR: ['OMR', '﷼', 'ر.ع.'],
  KWD: ['KWD', 'د.ك', 'KD'],
  BHD: ['BHD', 'BD', '.د.ب'],
  JOD: ['JOD', 'JD', 'د.ا'],
  EGP: ['EGP', '£', 'ج.م'],
  INR: ['INR', '₹', 'Rs', 'Rs.'],
  PKR: ['PKR', '₨', 'Rs'],
  IDR: ['IDR', 'Rp'],
  HKD: ['HKD', 'HK$', '$'],
  SGD: ['SGD', 'S$', '$'],
  TWD: ['TWD', 'NT$', '$'],
  AUD: ['AUD', 'A$', '$'],
  CAD: ['CAD', 'C$', '$'],
  NZD: ['NZD', 'NZ$', '$'],
  MXN: ['MXN', 'MX$', '$'],
  CHF: ['CHF', 'Fr', 'SFr'],
  CNY: ['¥', 'CNY', 'RMB'],
  JPY: ['¥', 'JP¥', 'JPY'],
  KRW: ['₩', 'KRW'],
  MYR: ['RM', 'MYR'],
  THB: ['฿', 'THB'],
  VND: ['₫', 'VND'],
  USD: ['$', 'US$', 'USD'],
  EUR: ['€', 'EUR'],
  GBP: ['£', 'GBP'],
}

function defaultSymbol(code: string): string {
  if (MULTI_SYMBOLS[code]) return MULTI_SYMBOLS[code][0]
  return currSymbol(code)
}

const ALL_CURRENCIES: string[] = (() => {
  try { return (Intl as any).supportedValuesOf('currency') as string[] }
  catch { return ['CNY','USD','EUR','GBP','JPY','KRW','THB','VND','MYR','SAR','HKD','SGD','TWD','AUD','CAD'] }
})()

const currLabelCache = new Map<string, string>()
function currLabel(code: string, locale: string): string {
  const key = `${locale}:${code}`
  if (!currLabelCache.has(key)) {
    try {
      const name = new Intl.DisplayNames([locale], { type: 'currency' }).of(code) ?? code
      currLabelCache.set(key, name)
    } catch { currLabelCache.set(key, code) }
  }
  return currLabelCache.get(key)!
}

function currSymbol(code: string): string {
  try {
    const parts = new Intl.NumberFormat('en', { style: 'currency', currency: code, currencyDisplay: 'narrowSymbol' })
      .formatToParts(0)
    return parts.find(p => p.type === 'currency')?.value ?? code
  } catch { return code }
}

function CurrencySelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const { i18n: i18nInst } = useTranslation()
  const locale = i18nInst.language
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState<DropdownPos | null>(null)
  const [query, setQuery] = useState('')
  const btnRef = useRef<HTMLButtonElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    function onOutside(e: MouseEvent) {
      if (
        btnRef.current && !btnRef.current.contains(e.target as Node) &&
        dropRef.current && !dropRef.current.contains(e.target as Node)
      ) { setOpen(false); setQuery('') }
    }
    document.addEventListener('mousedown', onOutside)
    return () => document.removeEventListener('mousedown', onOutside)
  }, [])

  useEffect(() => {
    if (open && inputRef.current) setTimeout(() => inputRef.current?.focus(), 50)
  }, [open])

  function handleOpen() {
    if (!open && btnRef.current) setPos(calcPos(btnRef.current))
    setOpen(o => !o)
  }

  const q = query.toLowerCase()
  const filtered = q
    ? ALL_CURRENCIES.filter(c =>
        c.toLowerCase().includes(q) ||
        currLabel(c, locale).toLowerCase().includes(q) ||
        currSymbol(c).toLowerCase().includes(q)
      ).slice(0, 80)
    : ALL_CURRENCIES

  const dropStyle: React.CSSProperties = pos ? {
    position: 'fixed', zIndex: 9999, left: pos.left, width: pos.width,
    ...(pos.openUp ? { bottom: window.innerHeight - pos.top } : { top: pos.top }),
    maxHeight: '300px', display: 'flex', flexDirection: 'column',
  } : {}

  return (
    <div className="relative">
      <button ref={btnRef} type="button" onClick={handleOpen}
        className="input flex items-center justify-between gap-2 cursor-pointer text-start w-full font-mono">
        <span className="truncate">{value || '—'}</span>
        <svg className={`w-4 h-4 text-gray-400 flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      </button>
      {open && pos && createPortal(
        <div ref={dropRef} style={dropStyle} className="bg-white border border-gray-200 rounded-lg shadow-xl">
          <div className="p-2 border-b border-gray-100 flex-shrink-0">
            <input ref={inputRef} className="input text-sm py-1.5 w-full"
              placeholder="CNY, 人民币…" value={query}
              onChange={e => setQuery(e.target.value)}
              onClick={e => e.stopPropagation()} />
          </div>
          <div className="overflow-y-auto flex-1">
            {filtered.map(c => (
              <button key={c} type="button"
                onClick={() => { onChange(c); setOpen(false); setQuery('') }}
                className={`w-full text-start px-3 py-2 text-sm hover:bg-gray-50 transition-colors flex items-center gap-3 ${c === value ? 'bg-brand-50 text-brand-600 font-semibold' : 'text-gray-700'}`}
              >
                <span className="font-mono w-10 flex-shrink-0">{c}</span>
                <span className="flex-1 truncate">{currLabel(c, locale)}</span>
                <span className="text-xs text-gray-400 flex-shrink-0 font-mono">{currSymbol(c)}</span>
              </button>
            ))}
            {filtered.length === 0 && <p className="px-3 py-4 text-sm text-gray-400 text-center">—</p>}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}

function CurrencySymbolField({ currencyCode, value, onChange }: {
  currencyCode: string; value: string; onChange: (v: string) => void
}) {
  const symbols = MULTI_SYMBOLS[currencyCode]
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState<DropdownPos | null>(null)
  const btnRef = useRef<HTMLButtonElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function onOutside(e: MouseEvent) {
      if (
        btnRef.current && !btnRef.current.contains(e.target as Node) &&
        dropRef.current && !dropRef.current.contains(e.target as Node)
      ) setOpen(false)
    }
    document.addEventListener('mousedown', onOutside)
    return () => document.removeEventListener('mousedown', onOutside)
  }, [])

  if (!symbols) {
    return (
      <input className="input w-28 font-mono" value={value}
        onChange={e => onChange(e.target.value)} placeholder="¥" />
    )
  }

  function handleOpen() {
    if (!open && btnRef.current) setPos(calcPos(btnRef.current))
    setOpen(o => !o)
  }

  const dropStyle: React.CSSProperties = pos ? {
    position: 'fixed', zIndex: 9999, left: pos.left, width: pos.width,
    ...(pos.openUp ? { bottom: window.innerHeight - pos.top } : { top: pos.top }),
  } : {}

  return (
    <div className="relative w-28">
      <button ref={btnRef} type="button" onClick={handleOpen}
        className="input flex items-center justify-between gap-2 cursor-pointer w-full font-mono">
        <span>{value || symbols[0]}</span>
        <svg className={`w-4 h-4 text-gray-400 flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      </button>
      {open && pos && createPortal(
        <div ref={dropRef} style={dropStyle} className="bg-white border border-gray-200 rounded-lg shadow-xl overflow-hidden">
          {symbols.map(s => (
            <button key={s} type="button"
              onClick={() => { onChange(s); setOpen(false) }}
              className={`w-full text-start px-4 py-2.5 text-sm font-mono hover:bg-gray-50 transition-colors flex items-center justify-between ${s === value ? 'bg-brand-50 text-brand-600 font-semibold' : 'text-gray-700'}`}
            >
              {s}
              {s === value && <span className="text-brand-500 text-xs">✓</span>}
            </button>
          ))}
        </div>,
        document.body
      )}
    </div>
  )
}

const ALL_TIMEZONES: string[] = (() => {
  try { return (Intl as any).supportedValuesOf('timeZone') as string[] }
  catch { return ['UTC','Asia/Shanghai','Asia/Tokyo','Asia/Seoul','Asia/Bangkok','Asia/Kuala_Lumpur','Asia/Riyadh','Asia/Ho_Chi_Minh','Europe/London','America/New_York','America/Los_Angeles'] }
})()

// Cache tzLabel results per locale to avoid 500+ Intl calls on every keystroke
const tzLabelCache = new Map<string, string>()
function tzLabelCached(tz: string, locale: string): string {
  const key = `${locale}:${tz}`
  if (!tzLabelCache.has(key)) tzLabelCache.set(key, tzLabel(tz, locale))
  return tzLabelCache.get(key)!
}

function TimezoneSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const { i18n: i18nInst } = useTranslation()
  const locale = i18nInst.language
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState<DropdownPos | null>(null)
  const [query, setQuery] = useState('')
  const btnRef = useRef<HTMLButtonElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    function onOutside(e: MouseEvent) {
      if (
        btnRef.current && !btnRef.current.contains(e.target as Node) &&
        dropRef.current && !dropRef.current.contains(e.target as Node)
      ) { setOpen(false); setQuery('') }
    }
    document.addEventListener('mousedown', onOutside)
    return () => document.removeEventListener('mousedown', onOutside)
  }, [])

  useEffect(() => {
    if (open && inputRef.current) setTimeout(() => inputRef.current?.focus(), 50)
  }, [open])

  function handleOpen() {
    if (!open && btnRef.current) setPos(calcPos(btnRef.current))
    setOpen(o => !o)
  }

  const filtered = query
    ? ALL_TIMEZONES.filter(tz =>
        tz.toLowerCase().includes(query.toLowerCase()) ||
        tzOffset(tz).toLowerCase().includes(query.toLowerCase()) ||
        tzLabelCached(tz, locale).toLowerCase().includes(query.toLowerCase())
      ).slice(0, 100)
    : ALL_TIMEZONES

  const dropStyle: React.CSSProperties = pos ? {
    position: 'fixed',
    zIndex: 9999,
    left: pos.left,
    width: pos.width,
    ...(pos.openUp ? { bottom: window.innerHeight - pos.top } : { top: pos.top }),
    maxHeight: '300px',
    display: 'flex',
    flexDirection: 'column',
  } : {}

  return (
    <div className="relative">
      <button
        ref={btnRef}
        type="button"
        onClick={handleOpen}
        className="input flex items-center justify-between gap-2 cursor-pointer text-start w-full"
      >
        <span className="truncate">{value ? `${tzLabel(value, locale)}  ${tzOffset(value)}` : 'UTC'}</span>
        <svg className={`w-4 h-4 text-gray-400 flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      </button>

      {open && pos && createPortal(
        <div ref={dropRef} style={dropStyle} className="bg-white border border-gray-200 rounded-lg shadow-xl">
          <div className="p-2 border-b border-gray-100 flex-shrink-0">
            <input
              ref={inputRef}
              className="input text-sm py-1.5 w-full"
              placeholder="Asia/Shanghai…"
              value={query}
              onChange={e => setQuery(e.target.value)}
              onClick={e => e.stopPropagation()}
            />
          </div>
          <div className="overflow-y-auto flex-1">
            {filtered.map(tz => (
              <button
                key={tz}
                type="button"
                onClick={() => { onChange(tz); setOpen(false); setQuery('') }}
                className={`w-full text-start px-3 py-2 text-sm hover:bg-gray-50 transition-colors flex items-center justify-between gap-2 ${tz === value ? 'bg-brand-50 text-brand-600 font-semibold' : 'text-gray-700'}`}
              >
                <span className="truncate flex-1">
                  <span className="block">{tzLabelCached(tz, locale)}</span>
                  <span className="block text-xs text-gray-400 font-mono">{tz}</span>
                </span>
                <span className="text-xs text-gray-400 flex-shrink-0 font-mono">{tzOffset(tz)}</span>
              </button>
            ))}
            {filtered.length === 0 && <p className="px-3 py-4 text-sm text-gray-400 text-center">—</p>}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}
