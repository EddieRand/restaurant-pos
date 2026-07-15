import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import type { RegionConfig, AycePackage } from '../api/admin'
import { useRegionConfig } from '../hooks/useRegionConfig'
import { PadPreview } from '../components/PadPreview'

const LOCALE_OPTIONS = ['en', 'zh', 'ja', 'ko', 'th', 'vi', 'ms', 'ar'] as const

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

function emptyPackage(): AycePackage {
  return {
    id: crypto.randomUUID(),
    name: '',
    description: '',
    totalRoundsLimit: null,
    maxItemsPerRound: 8,
    minMinutesBetweenRounds: 15,
    priceMinorUnit: 0,
  }
}

/* ==================================================================== */
/*  Page component                                                      */
/* ==================================================================== */
export default function PadConfigPage() {
  const { t } = useTranslation()
  const { config, loading, error, saving, saved, save, setConfig } = useRegionConfig()

  /* ---- patch helper ---- */
  const patch = useCallback(<K extends keyof RegionConfig['padConfig']>(
    key: K,
    value: RegionConfig['padConfig'][K],
  ) => {
    setConfig(prev => prev && {
      ...prev,
      padConfig: { ...prev.padConfig, [key]: value },
    })
  }, [setConfig])

  if (loading) return <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
  if (error) return <div className="p-8 text-sm text-red-500">{t('common.loadConfigFailed', { error })}</div>
  if (!config) return <div className="p-8 text-sm text-gray-400">{t('common.configNotLoaded')}</div>

  const pad = config.padConfig

  const updatePackage = (id: string, patchFields: Partial<AycePackage>) => {
    patch('aycePackages', pad.aycePackages.map(p => p.id === id ? { ...p, ...patchFields } : p))
  }
  const removePackage = (id: string) => {
    patch('aycePackages', pad.aycePackages.filter(p => p.id !== id))
  }
  const addPackage = () => {
    patch('aycePackages', [...pad.aycePackages, emptyPackage()])
  }

  const updatePromoLine = (index: number, value: string) => {
    patch('idlePromoLines', pad.idlePromoLines.map((line, i) => i === index ? value : line))
  }
  const removePromoLine = (index: number) => {
    patch('idlePromoLines', pad.idlePromoLines.filter((_, i) => i !== index))
  }
  const addPromoLine = () => {
    patch('idlePromoLines', [...pad.idlePromoLines, ''])
  }

  /* ── render ─────────────────────────────────────────────────────── */
  return (
    <div className="max-w-6xl mx-auto p-8">
      {/* header */}
      <div className="mb-8">
        <h2 className="text-lg font-bold text-gray-900">{t('pad.title')}</h2>
        <p className="text-sm text-gray-400 mt-0.5">{t('pad.subtitle')}</p>
      </div>

      <div className="space-y-10">

      {/* ═════════════════ 实时预览（横屏平板） ═════════════════ */}
      <section className="space-y-3">
        <div className="max-w-3xl">
          <PadPreview pad={pad} />
        </div>
      </section>

      {/* ═════════════════ AYCE 自助餐模式 ═════════════════ */}
      <section className="space-y-5">
        <SectionTitle>{t('pad.sectionAyce')}</SectionTitle>

        <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
          <input
            type="checkbox" className="rounded"
            checked={pad.ayceEnabled}
            onChange={e => patch('ayceEnabled', e.target.checked)}
          />
          {t('pad.ayceEnabled')}
        </label>

        {pad.ayceEnabled && (
          <div className="space-y-5 ml-6">
            <div className="grid grid-cols-3 gap-4">
              <Field label={t('pad.maxItemsPerRound')}>
                <input
                  className="input w-full font-mono text-sm"
                  type="number" min={0} max={50}
                  value={pad.maxItemsPerRound}
                  onChange={e => patch('maxItemsPerRound', Number(e.target.value))}
                />
              </Field>
              <Field label={t('pad.minMinutesBetweenRounds')}>
                <input
                  className="input w-full font-mono text-sm"
                  type="number" min={0} max={180}
                  value={pad.minMinutesBetweenRounds}
                  onChange={e => patch('minMinutesBetweenRounds', Number(e.target.value))}
                />
              </Field>
              <Field label={t('pad.totalRoundsLimit')}>
                <input
                  className="input w-full font-mono text-sm"
                  type="number" min={0}
                  value={pad.totalRoundsLimit ?? ''}
                  placeholder={t('pad.unlimited')}
                  onChange={e => patch('totalRoundsLimit', e.target.value === '' ? null : Number(e.target.value))}
                />
              </Field>
            </div>

            {/* AYCE packages */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-gray-500">{t('pad.aycePackages')}</span>
                <button className="btn-secondary text-xs" onClick={addPackage}>{t('pad.addPackage')}</button>
              </div>
              {pad.aycePackages.length === 0 && (
                <p className="text-xs text-gray-400">{t('pad.noPackages')}</p>
              )}
              {pad.aycePackages.map(pkg => (
                <div key={pkg.id} className="border border-gray-200 rounded-lg p-4 space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <Field label={t('pad.packageName')}>
                      <input
                        className="input w-full font-mono text-sm"
                        type="text"
                        value={pkg.name}
                        onChange={e => updatePackage(pkg.id, { name: e.target.value })}
                      />
                    </Field>
                    <Field label={t('pad.packagePrice')}>
                      <input
                        className="input w-full font-mono text-sm"
                        type="number" min={0}
                        value={pkg.priceMinorUnit}
                        onChange={e => updatePackage(pkg.id, { priceMinorUnit: Number(e.target.value) })}
                      />
                    </Field>
                  </div>
                  <Field label={t('pad.packageDescription')}>
                    <input
                      className="input w-full font-mono text-sm"
                      type="text"
                      value={pkg.description}
                      onChange={e => updatePackage(pkg.id, { description: e.target.value })}
                    />
                  </Field>
                  <div className="grid grid-cols-3 gap-3">
                    <Field label={t('pad.maxItemsPerRound')}>
                      <input
                        className="input w-full font-mono text-sm"
                        type="number" min={0}
                        value={pkg.maxItemsPerRound}
                        onChange={e => updatePackage(pkg.id, { maxItemsPerRound: Number(e.target.value) })}
                      />
                    </Field>
                    <Field label={t('pad.minMinutesBetweenRounds')}>
                      <input
                        className="input w-full font-mono text-sm"
                        type="number" min={0}
                        value={pkg.minMinutesBetweenRounds}
                        onChange={e => updatePackage(pkg.id, { minMinutesBetweenRounds: Number(e.target.value) })}
                      />
                    </Field>
                    <Field label={t('pad.totalRoundsLimit')}>
                      <input
                        className="input w-full font-mono text-sm"
                        type="number" min={0}
                        value={pkg.totalRoundsLimit ?? ''}
                        placeholder={t('pad.unlimited')}
                        onChange={e => updatePackage(pkg.id, { totalRoundsLimit: e.target.value === '' ? null : Number(e.target.value) })}
                      />
                    </Field>
                  </div>
                  <button className="text-xs text-red-500" onClick={() => removePackage(pkg.id)}>{t('pad.remove')}</button>
                </div>
              ))}
            </div>
          </div>
        )}
      </section>

      {/* ═════════════════ 待机屏 ═════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('pad.sectionIdle')}</SectionTitle>

        <Field label={t('pad.idleTimeoutSeconds')}>
          <div className="flex items-center gap-1">
            <input
              className="input w-24 font-mono text-sm"
              type="number" min={10} max={600}
              value={pad.idleTimeoutSeconds}
              onChange={e => patch('idleTimeoutSeconds', Number(e.target.value))}
            />
            <span className="text-xs text-gray-400">s</span>
          </div>
        </Field>

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">{t('pad.idlePromoLines')}</span>
            <button className="btn-secondary text-xs" onClick={addPromoLine}>{t('pad.addPromoLine')}</button>
          </div>
          {pad.idlePromoLines.length === 0 && (
            <p className="text-xs text-gray-400">{t('pad.noPromoLines')}</p>
          )}
          {pad.idlePromoLines.map((line, i) => (
            <div key={i} className="flex items-center gap-2">
              <input
                className="input flex-1 font-mono text-sm"
                type="text"
                value={line}
                onChange={e => updatePromoLine(i, e.target.value)}
              />
              <button className="text-xs text-red-500" onClick={() => removePromoLine(i)}>{t('pad.remove')}</button>
            </div>
          ))}
        </div>
      </section>

      {/* ═════════════════ 语言与行为 ═════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('pad.sectionBehavior')}</SectionTitle>

        <div className="space-y-2">
          <span className="block text-xs font-medium text-gray-500">{t('pad.supportedLocales')}</span>
          <div className="flex flex-wrap gap-3">
            {LOCALE_OPTIONS.map(loc => (
              <label key={loc} className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none border border-gray-200 rounded-lg px-3 py-2">
                <input
                  type="checkbox" className="rounded"
                  checked={pad.supportedLocales.includes(loc)}
                  onChange={e => {
                    const next = e.target.checked
                      ? [...pad.supportedLocales, loc]
                      : pad.supportedLocales.filter(x => x !== loc)
                    patch('supportedLocales', next)
                  }}
                />
                {loc}
              </label>
            ))}
          </div>
        </div>

        <div className="space-y-3">
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={pad.showNutritionInfo}
              onChange={e => patch('showNutritionInfo', e.target.checked)}
            />
            {t('pad.showNutritionInfo')}
          </label>
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={pad.allowItemNotes}
              onChange={e => patch('allowItemNotes', e.target.checked)}
            />
            {t('pad.allowItemNotes')}
          </label>
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={pad.showRunningTotal}
              onChange={e => patch('showRunningTotal', e.target.checked)}
            />
            {t('pad.showRunningTotal')}
          </label>
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={pad.showOrderHistory}
              onChange={e => patch('showOrderHistory', e.target.checked)}
            />
            {t('pad.showOrderHistory')}
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
    </div>
  )
}
