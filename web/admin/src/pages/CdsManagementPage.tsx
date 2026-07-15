import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import type { RegionConfig } from '../api/admin'
import { useRegionConfig } from '../hooks/useRegionConfig'
import { CdsPreview } from '../components/CdsPreview'

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="block text-xs font-medium text-gray-500 mb-1">{label}</span>
      {hint && <span className="block text-xs text-gray-400 mb-1">{hint}</span>}
      {children}
    </label>
  )
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h3 className="text-sm font-semibold text-gray-800">{children}</h3>
}

export default function CdsManagementPage() {
  const { t } = useTranslation()
  const { config, loading, error, saving, saved, save, setConfig } = useRegionConfig()

  const patch = useCallback(<K extends keyof NonNullable<RegionConfig['cdsConfig']>>(
    key: K,
    value: NonNullable<RegionConfig['cdsConfig']>[K],
  ) => {
    setConfig(prev => prev && {
      ...prev,
      cdsConfig: { ...prev.cdsConfig, [key]: value },
    })
  }, [setConfig])

  if (loading) return <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
  if (error) return <div className="p-8 text-sm text-red-500">{t('common.loadConfigFailed', { error })}</div>
  if (!config) return <div className="p-8 text-sm text-gray-400">{t('common.configNotLoaded')}</div>

  const cds = config.cdsConfig

  return (
    <div className="max-w-6xl mx-auto p-8">
      {/* header */}
      <div className="mb-8">
        <h2 className="text-lg font-bold text-gray-900">{t('cds.title')}</h2>
        <p className="text-sm text-gray-400 mt-0.5">{t('cds.subtitle')}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-8 items-start">
      <div className="space-y-10">

      {/* ═════════════════ 基本设置 ═════════════════ */}
      <section className="space-y-5">
        <SectionTitle>{t('cds.sectionBasic')}</SectionTitle>

        <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
          <input
            type="checkbox" className="rounded"
            checked={cds.enabled}
            onChange={e => patch('enabled', e.target.checked)}
          />
          {t('cds.enabled')}
        </label>

        <Field label={t('cds.displayName')}>
          <input
            className="input w-full font-mono text-sm"
            type="text"
            value={cds.displayName}
            onChange={e => patch('displayName', e.target.value)}
            placeholder={t('cds.displayNamePlaceholder')}
          />
        </Field>
      </section>

      {/* ═════════════════ 欢迎/待机画面 ═════════════════ */}
      <section className={`space-y-5 border-t border-gray-200 pt-8 ${!cds.enabled ? 'opacity-40 pointer-events-none' : ''}`}>
        <SectionTitle>{t('cds.sectionWelcome')}</SectionTitle>

        <div className="grid grid-cols-2 gap-4">
          <Field label={t('cds.welcomeTitle')}>
            <input
              className="input w-full font-mono text-sm"
              type="text"
              value={cds.welcomeTitle}
              onChange={e => patch('welcomeTitle', e.target.value)}
              placeholder={t('cds.welcomeTitlePlaceholder')}
            />
          </Field>
          <Field label={t('cds.welcomeSubtitle')}>
            <input
              className="input w-full font-mono text-sm"
              type="text"
              value={cds.welcomeSubtitle}
              onChange={e => patch('welcomeSubtitle', e.target.value)}
              placeholder={t('cds.welcomeSubtitlePlaceholder')}
            />
          </Field>
        </div>

        <Field label={t('cds.logoUrl')} hint={t('cds.noLogo')}>
          <input
            className="input w-full font-mono text-sm"
            type="text"
            value={cds.logoUrl}
            onChange={e => patch('logoUrl', e.target.value)}
            placeholder="https://example.com/logo.png"
          />
        </Field>
      </section>

      {/* ═════════════════ 订单展示 ═════════════════ */}
      <section className={`space-y-5 border-t border-gray-200 pt-8 ${!cds.enabled ? 'opacity-40 pointer-events-none' : ''}`}>
        <SectionTitle>{t('cds.sectionOrder')}</SectionTitle>

        <div className="space-y-3">
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={cds.showOrderItems}
              onChange={e => patch('showOrderItems', e.target.checked)}
            />
            {t('cds.showOrderItems')}
          </label>
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={cds.showRunningTotal}
              onChange={e => patch('showRunningTotal', e.target.checked)}
            />
            {t('cds.showRunningTotal')}
          </label>
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={cds.showModifiers}
              onChange={e => patch('showModifiers', e.target.checked)}
            />
            {t('cds.showModifiers')}
          </label>
        </div>
      </section>

      {/* ═════════════════ 完成画面 ═════════════════ */}
      <section className={`space-y-5 border-t border-gray-200 pt-8 ${!cds.enabled ? 'opacity-40 pointer-events-none' : ''}`}>
        <SectionTitle>{t('cds.sectionCompletion')}</SectionTitle>

        <div className="grid grid-cols-2 gap-4">
          <Field label={t('cds.completionTitle')}>
            <input
              className="input w-full font-mono text-sm"
              type="text"
              value={cds.completionTitle}
              onChange={e => patch('completionTitle', e.target.value)}
              placeholder={t('cds.completionTitlePlaceholder')}
            />
          </Field>
          <Field label={t('cds.completionSubtitle')}>
            <input
              className="input w-full font-mono text-sm"
              type="text"
              value={cds.completionSubtitle}
              onChange={e => patch('completionSubtitle', e.target.value)}
              placeholder={t('cds.completionSubtitlePlaceholder')}
            />
          </Field>
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

      <CdsPreview cds={cds} currencySymbol={config.currencySymbol} minorDigits={config.currencyMinorDigits} />
      </div>
    </div>
  )
}
