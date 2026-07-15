import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  DEFAULT_QR_ORDERING_CONFIG,
  qrOrderingApi,
  type QrCode,
  type QrOrderingConfig,
  type TableQrBinding,
  type TableQrBindingResponse,
} from '../api/admin'
import { QrMenuPreview } from '../components/QrMenuPreview'

type OtherScope = 'PICKUP' | 'DELIVERY' | 'MENU'

const OTHER_ENTRANCES: Array<{
  scope: OtherScope
  titleKey: string
  descKey: string
  createKey: string
  color: string
}> = [
  { scope: 'PICKUP', titleKey: 'pickupTitle', descKey: 'pickupDesc', createKey: 'pickupCreate', color: 'border-blue-100 bg-blue-50 text-blue-700' },
  { scope: 'DELIVERY', titleKey: 'deliveryTitle', descKey: 'deliveryDesc', createKey: 'deliveryCreate', color: 'border-emerald-100 bg-emerald-50 text-emerald-700' },
  { scope: 'MENU', titleKey: 'menuTitle', descKey: 'menuDesc', createKey: 'menuCreate', color: 'border-gray-200 bg-gray-50 text-gray-700' },
]

function newCode(scope: string) {
  const prefix = scope === 'PICKUP' ? 'pickup' : scope === 'DELIVERY' ? 'delivery' : 'menu'
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}`
}

function statusClass(binding: TableQrBinding) {
  if (!binding.currentQr && binding.disabledCodeCount > 0) return 'bg-amber-50 text-amber-700'
  if (!binding.currentQr) return 'bg-gray-100 text-gray-500'
  return 'bg-emerald-50 text-emerald-700'
}

export default function QrOrderingPage() {
  const { t } = useTranslation()
  const [config, setConfig] = useState<QrOrderingConfig>(DEFAULT_QR_ORDERING_CONFIG)
  const [bindings, setBindings] = useState<TableQrBindingResponse>({ sections: [], unassignedCodes: [] })
  const [codes, setCodes] = useState<QrCode[]>([])
  const [activeSection, setActiveSection] = useState('')
  const [rebindTarget, setRebindTarget] = useState<TableQrBinding | null>(null)
  const [selectedCode, setSelectedCode] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const baseUrl = useMemo(() => `${window.location.origin}/qr/?code=`, [])
  const active = bindings.sections.find(section => section.id === activeSection) ?? bindings.sections[0]
  const tableCodes = codes.filter(code => code.scope === 'TABLE')
  const otherCodes = codes.filter(code => code.scope !== 'TABLE')

  function entranceName(scope: string) {
    if (scope === 'PICKUP') return t('qrOrdering.pickupTitle')
    if (scope === 'DELIVERY') return t('qrOrdering.deliveryTitle')
    if (scope === 'MENU') return t('qrOrdering.menuTitle')
    return t('qrOrdering.tableOrdering')
  }

  function statusLabel(binding: TableQrBinding) {
    if (!binding.currentQr && binding.disabledCodeCount > 0) return t('qrOrdering.statusDisabled')
    if (!binding.currentQr) return t('qrOrdering.statusNotGenerated')
    return binding.currentQr.enabled ? t('qrOrdering.statusActive') : t('qrOrdering.statusDisabled')
  }

  async function load() {
    setLoading(true)
    setError('')
    try {
      const [cfg, tableData, codeList] = await Promise.all([
        qrOrderingApi.getConfig(),
        qrOrderingApi.tableBindings(),
        qrOrderingApi.listCodes(),
      ])
      if (!tableData || !Array.isArray(tableData.sections)) {
        throw new Error(t('qrOrdering.invalidResponse'))
      }
      setConfig({ ...DEFAULT_QR_ORDERING_CONFIG, ...cfg })
      setBindings(tableData)
      setCodes(Array.isArray(codeList) ? codeList : [])
      setActiveSection(current => current || tableData.sections[0]?.id || '')
    } catch (err) {
      setError(err instanceof Error ? err.message : t('qrOrdering.loadFailed'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function saveConfig(next: QrOrderingConfig) {
    setConfig(next)
    setSaving(true)
    try {
      const saved = await qrOrderingApi.saveConfig(next)
      setConfig({ ...DEFAULT_QR_ORDERING_CONFIG, ...saved })
      setMessage(t('qrOrdering.configSaved'))
    } finally {
      setSaving(false)
    }
  }

  async function runAction(action: () => Promise<unknown>, done: string) {
    setSaving(true)
    setMessage('')
    try {
      await action()
      await load()
      setMessage(done)
    } finally {
      setSaving(false)
    }
  }

  function copyUrl(url: string) {
    navigator.clipboard?.writeText(url)
    setMessage(t('qrOrdering.linkCopied'))
  }

  function printUrl(binding: TableQrBinding) {
    const url = binding.customerUrl
    if (!url || !binding.currentQr) return
    const win = window.open('', '_blank', 'width=420,height=560')
    win?.document.write(`
      <title>${binding.tableName} ${t('qrOrdering.printTableCard')}</title>
      <body style="font-family: system-ui, sans-serif; padding: 32px; text-align: center;">
        <h1 style="margin: 0 0 8px;">${binding.tableName}</h1>
        <p style="margin: 0 0 24px; color: #555;">${t('qrOrdering.scanToOrder')}</p>
        <div style="border: 2px solid #111; padding: 24px; word-break: break-all;">${url}</div>
        <p style="margin-top: 16px; color: #777;">${binding.currentQr.code}</p>
      </body>
    `)
    win?.document.close()
    win?.print()
  }

  async function batchGenerate(sectionId: string) {
    const section = bindings.sections.find(item => item.id === sectionId)
    if (!section) return
    const missing = section.tables.filter(table => !table.currentQr)
    await runAction(
      async () => {
        for (const table of missing) await qrOrderingApi.generateTableQr(table.tableId)
      },
      t('qrOrdering.batchGenerated', { count: missing.length }),
    )
  }

  async function confirmRebind() {
    if (!rebindTarget || !selectedCode) return
    await runAction(
      () => qrOrderingApi.rebindCode(selectedCode, rebindTarget.tableId),
      t('qrOrdering.rebindSuccess', { code: selectedCode, tableName: rebindTarget.tableName }),
    )
    setRebindTarget(null)
    setSelectedCode('')
  }

  async function createOther(scope: OtherScope) {
    await runAction(
      () => qrOrderingApi.createCode({ code: newCode(scope), scope, tableId: null, enabled: true, expiresAt: null }),
      t('qrOrdering.entranceCreated', { name: entranceName(scope) }),
    )
  }

  if (loading) return <div className="p-8 text-sm text-gray-400">{t('qrOrdering.loading')}</div>

  if (error) {
    return (
      <div className="p-8 max-w-3xl">
        <div className="card p-6 border-red-100 bg-red-50">
          <h1 className="text-lg font-semibold text-red-700">{t('qrOrdering.loadFailed')}</h1>
          <p className="mt-2 text-sm text-red-600">{error}</p>
          <button className="btn-secondary mt-4" onClick={load}>{t('qrOrdering.retry')}</button>
        </div>
      </div>
    )
  }

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">{t('qrOrdering.title')}</h1>
          <p className="mt-1 text-sm text-gray-500">{t('qrOrdering.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            className="input w-36"
            value={config.paymentTiming}
            onChange={e => saveConfig({ ...config, paymentTiming: e.target.value as QrOrderingConfig['paymentTiming'] })}
            disabled={saving}
          >
            <option value="PAY_AT_END">{t('qrOrdering.paymentPayAtEnd')}</option>
            <option value="PAY_BEFORE_SUBMIT">{t('qrOrdering.paymentPayBefore')}</option>
            <option value="PAY_AFTER_SUBMIT">{t('qrOrdering.paymentPayAfter')}</option>
            <option value="STAFF_COLLECTS">{t('qrOrdering.paymentStaffCollects')}</option>
          </select>
          <label className="flex items-center gap-2 text-sm text-gray-600">
            <input
              type="checkbox"
              checked={config.enabled}
              onChange={e => saveConfig({ ...config, enabled: e.target.checked })}
              disabled={saving}
            />
            {t('qrOrdering.enableQrOrdering')}
          </label>
        </div>
      </div>

      {message && <div className="rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{message}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-8 items-start">
      <div className="space-y-6">

      <section className="card space-y-4 p-5">
        <h2 className="text-lg font-semibold text-gray-900">{t('qrOrdering.appearanceTitle')}</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <label className="block">
            <span className="block text-xs font-medium text-gray-500 mb-1">{t('qrOrdering.themeColor')}</span>
            <div className="flex items-center gap-2">
              <input
                type="color"
                className="h-9 w-12 rounded border border-gray-200"
                value={config.appearance.themeColor}
                disabled={saving}
                onChange={e => saveConfig({ ...config, appearance: { ...config.appearance, themeColor: e.target.value } })}
              />
              <input
                type="text"
                className="input flex-1 font-mono text-sm"
                value={config.appearance.themeColor}
                disabled={saving}
                onChange={e => saveConfig({ ...config, appearance: { ...config.appearance, themeColor: e.target.value } })}
              />
            </div>
          </label>
          <label className="block">
            <span className="block text-xs font-medium text-gray-500 mb-1">{t('qrOrdering.menuCardStyle')}</span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={saving}
                className={`flex-1 rounded-lg border px-3 py-2 text-sm ${config.appearance.menuCardStyle === 'grid' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 bg-white text-gray-600'}`}
                onClick={() => saveConfig({ ...config, appearance: { ...config.appearance, menuCardStyle: 'grid' } })}
              >
                {t('qrOrdering.menuCardStyleGrid')}
              </button>
              <button
                type="button"
                disabled={saving}
                className={`flex-1 rounded-lg border px-3 py-2 text-sm ${config.appearance.menuCardStyle === 'list' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 bg-white text-gray-600'}`}
                onClick={() => saveConfig({ ...config, appearance: { ...config.appearance, menuCardStyle: 'list' } })}
              >
                {t('qrOrdering.menuCardStyleList')}
              </button>
            </div>
          </label>
          <label className="block sm:col-span-2">
            <span className="block text-xs font-medium text-gray-500 mb-1">{t('qrOrdering.bannerImageUrl')}</span>
            <input
              type="text"
              className="input w-full font-mono text-sm"
              placeholder={t('qrOrdering.bannerPlaceholderHint')}
              value={config.appearance.bannerImageUrl}
              disabled={saving}
              onChange={e => saveConfig({ ...config, appearance: { ...config.appearance, bannerImageUrl: e.target.value } })}
            />
            <span className="mt-1 block text-[11px] text-gray-400">{t('qrOrdering.bannerPlaceholderHint')}</span>
          </label>
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-2">
            {bindings.sections.map(section => (
              <button
                key={section.id}
                className={`rounded-lg border px-4 py-2 text-sm font-medium ${section.id === active?.id ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'}`}
                onClick={() => setActiveSection(section.id)}
              >
                {section.name}
              </button>
            ))}
          </div>
          {active && (
            <button
              className="btn-secondary"
              disabled={saving || active.tables.every(table => table.currentQr)}
              onClick={() => batchGenerate(active.id)}
            >
              {t('qrOrdering.batchGenerate')}
            </button>
          )}
        </div>

        {!active ? (
          <div className="rounded-lg border border-dashed border-gray-200 py-12 text-center text-sm text-gray-400">
            {t('qrOrdering.noTables')}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 2xl:grid-cols-3 gap-4">
            {active.tables.map(table => {
              const url = table.customerUrl ?? (table.currentQr ? `${baseUrl}${encodeURIComponent(table.currentQr.code)}` : '')
              return (
                <div key={table.tableId} className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="text-lg font-semibold text-gray-900">{table.tableName}</div>
                      <div className="mt-1 text-xs text-gray-500">{table.sectionName} · {t('qrOrdering.peopleSeats', { capacity: table.capacity })}</div>
                    </div>
                    <span className={`badge ${statusClass(table)}`}>{statusLabel(table)}</span>
                  </div>

                  {table.currentQr ? (
                    <div className="mt-4 space-y-3">
                      <div className="aspect-square max-w-[120px] rounded-lg border border-gray-200 bg-gray-50 grid place-items-center text-xs text-gray-400">
                        {t('qrOrdering.qrcode')}
                      </div>
                      <div className="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 text-xs text-gray-600 break-all">{url}</div>
                      <div className="text-xs text-gray-400">{t('qrOrdering.disabledCodeCount', { count: table.disabledCodeCount })}</div>
                      <div className="flex flex-wrap gap-2">
                        <button className="btn-secondary" onClick={() => copyUrl(url)}>{t('qrOrdering.copyLink')}</button>
                        <button className="btn-secondary" onClick={() => printUrl(table)}>{t('qrOrdering.printTableCard')}</button>
                        <button className="btn-secondary" onClick={() => setRebindTarget(table)}>{t('qrOrdering.rebind')}</button>
                        <button className="btn-secondary" disabled={saving} onClick={() => runAction(() => qrOrderingApi.updateCode(table.currentQr!.code, { enabled: false }), t('qrOrdering.disabled'))}>{t('qrOrdering.disable')}</button>
                        <button className="btn-primary" disabled={saving} onClick={() => runAction(() => qrOrderingApi.resetTableQr(table.tableId), t('qrOrdering.resetQrSuccess'))}>{t('qrOrdering.resetQr')}</button>
                      </div>
                    </div>
                  ) : (
                    <div className="mt-4 space-y-3">
                      <div className="rounded-lg border border-dashed border-gray-200 py-8 text-center text-sm text-gray-400">
                        {t('qrOrdering.noQrYet')}
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <button className="btn-primary" disabled={saving} onClick={() => runAction(() => qrOrderingApi.generateTableQr(table.tableId), t('qrOrdering.qrGenerated'))}>{t('qrOrdering.generateQr')}</button>
                        {table.disabledCodeCount > 0 && (
                          <button className="btn-secondary" disabled={saving} onClick={() => runAction(() => qrOrderingApi.generateTableQr(table.tableId), t('qrOrdering.qrReEnabled'))}>{t('qrOrdering.reEnable')}</button>
                        )}
                        <button className="btn-secondary" onClick={() => setRebindTarget(table)}>{t('qrOrdering.rebindPhysical')}</button>
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </section>

      <section className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">{t('qrOrdering.otherEntrances')}</h2>
          <p className="mt-1 text-sm text-gray-500">{t('qrOrdering.otherDesc')}</p>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          {OTHER_ENTRANCES.map(entrance => {
            const scopeCodes = otherCodes.filter(code => code.scope === entrance.scope)
            return (
              <div key={entrance.scope} className={`rounded-lg border p-4 ${entrance.color}`}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-semibold text-gray-900">{t(`qrOrdering.${entrance.titleKey}`)}</h3>
                    <p className="mt-1 text-sm opacity-80">{t(`qrOrdering.${entrance.descKey}`)}</p>
                  </div>
                  <button className="btn-secondary bg-white/80" disabled={saving} onClick={() => createOther(entrance.scope)}>
                    {t(`qrOrdering.${entrance.createKey}`)}
                  </button>
                </div>
                <div className="mt-4 space-y-3">
                  {scopeCodes.length === 0 ? (
                    <div className="rounded-lg border border-white/70 bg-white/50 py-6 text-center text-sm opacity-70">{t('qrOrdering.noEntrance')}</div>
                  ) : scopeCodes.map(code => {
                    const url = `${baseUrl}${encodeURIComponent(code.code)}`
                    return (
                      <div key={code.code} className="rounded-lg border border-white/70 bg-white/80 p-3">
                        <div className="flex items-center justify-between gap-2">
                          <div className="font-medium text-gray-900">{entranceName(code.scope)}</div>
                          <span className={`badge ${code.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-gray-100 text-gray-500'}`}>{code.enabled ? t('qrOrdering.statusActive') : t('qrOrdering.statusDisabled')}</span>
                        </div>
                        <div className="mt-2 font-mono text-xs text-gray-500 break-all">{url}</div>
                        <div className="mt-3 flex flex-wrap gap-3 text-xs">
                          <button className="font-medium text-brand-700" onClick={() => copyUrl(url)}>{t('qrOrdering.copyLink')}</button>
                          <button className="font-medium text-gray-700" disabled={saving} onClick={() => runAction(() => qrOrderingApi.updateCode(code.code, { enabled: !code.enabled }), code.enabled ? t('qrOrdering.entranceDisabled') : t('qrOrdering.entranceEnabled'))}>
                            {code.enabled ? t('qrOrdering.disable') : t('qrOrdering.enable')}
                          </button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      </section>

      </div>
      <QrMenuPreview appearance={config.appearance} />
      </div>

      {rebindTarget && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/30 p-4">
          <div className="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl">
            <h2 className="text-lg font-semibold text-gray-900">{t('qrOrdering.rebindTitle')}</h2>
            <p className="mt-2 text-sm text-gray-500">{t('qrOrdering.rebindDesc', { tableName: rebindTarget.tableName })}</p>
            <label className="mt-5 block">
              <span className="block text-xs font-medium text-gray-500 mb-1">{t('qrOrdering.existingQr')}</span>
              <select className="input" value={selectedCode} onChange={e => setSelectedCode(e.target.value)}>
                <option value="">{t('qrOrdering.pleaseSelect')}</option>
                {tableCodes.map(code => (
                  <option key={code.code} value={code.code}>
                    {code.code} {code.enabled ? t('qrOrdering.statusActive') : t('qrOrdering.statusDisabled')}
                  </option>
                ))}
              </select>
            </label>
            <div className="mt-6 flex justify-end gap-3">
              <button className="btn-secondary" onClick={() => { setRebindTarget(null); setSelectedCode('') }}>{t('qrOrdering.cancel')}</button>
              <button className="btn-primary" disabled={!selectedCode || saving} onClick={confirmRebind}>{t('qrOrdering.confirmRebind')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
