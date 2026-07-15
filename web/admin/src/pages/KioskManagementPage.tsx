import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import type { RegionConfig } from '../api/admin'
import { useRegionConfig } from '../hooks/useRegionConfig'
import { KioskPreview } from '../components/KioskPreview'

const PAYMENT_OPTIONS = ['CASH', 'CARD', 'QR_PAY', 'MEMBERSHIP'] as const
const QR_EC_OPTIONS = ['L', 'M', 'Q', 'H'] as const

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

/* ==================================================================== */
/*  Page component                                                      */
/* ==================================================================== */
export default function KioskManagementPage() {
  const { t } = useTranslation()
  const { config, loading, error, saving, saved, save, setConfig } = useRegionConfig()

  /* ---- patch helper ---- */
  const patch = useCallback(<K extends keyof NonNullable<RegionConfig['kioskConfig']>>(
    key: K,
    value: NonNullable<RegionConfig['kioskConfig']>[K],
  ) => {
    setConfig(prev => prev && {
      ...prev,
      kioskConfig: { ...prev.kioskConfig, [key]: value },
    })
  }, [setConfig])

  if (loading) return <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
  if (error) return <div className="p-8 text-sm text-red-500">{t('common.loadConfigFailed', { error })}</div>
  if (!config) return <div className="p-8 text-sm text-gray-400">{t('common.configNotLoaded')}</div>

  const kiosk = config.kioskConfig

  /* ── render ─────────────────────────────────────────────────────── */
  return (
    <div className="max-w-6xl mx-auto p-8">
      {/* header */}
      <div className="mb-8">
        <h2 className="text-lg font-bold text-gray-900">{t('kiosk.title')}</h2>
        <p className="text-sm text-gray-400 mt-0.5">{t('kiosk.subtitle')}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-8 items-start">
      <div className="space-y-10">

      {/* ═════════════════ 欢迎页配置 ═════════════════ */}
      <section className="space-y-5">
        <SectionTitle>{t('kiosk.sectionWelcome')}</SectionTitle>

        <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
          <input
            type="checkbox" className="rounded"
            checked={kiosk.showWelcomeScreen}
            onChange={e => patch('showWelcomeScreen', e.target.checked)}
          />
          {t('kiosk.showWelcomeScreen')}
        </label>

        {kiosk.showWelcomeScreen && (
          <div className="space-y-3 ml-6">
            <Field label={t('kiosk.welcomeTitle')}>
              <input
                className="input w-full font-mono text-sm"
                type="text"
                value={kiosk.welcomeTitle}
                onChange={e => patch('welcomeTitle', e.target.value)}
                placeholder={t('kiosk.welcomeTitleHint')}
              />
            </Field>
            <Field label={t('kiosk.welcomeSubtitle')}>
              <input
                className="input w-full font-mono text-sm"
                type="text"
                value={kiosk.welcomeSubtitle}
                onChange={e => patch('welcomeSubtitle', e.target.value)}
                placeholder={t('kiosk.welcomeSubtitleHint')}
              />
            </Field>
          </div>
        )}
      </section>

      {/* ═════════════════ 完成页配置 ═════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('kiosk.sectionCompletion')}</SectionTitle>

        <div className="space-y-3">
          <Field label={t('kiosk.completionTitle')}>
            <input
              className="input w-full font-mono text-sm"
              type="text"
              value={kiosk.completionTitle}
              onChange={e => patch('completionTitle', e.target.value)}
              placeholder={t('kiosk.completionTitleHint')}
            />
          </Field>
          <Field label={t('kiosk.completionSubtitle')}>
            <input
              className="input w-full font-mono text-sm"
              type="text"
              value={kiosk.completionSubtitle}
              onChange={e => patch('completionSubtitle', e.target.value)}
              placeholder={t('kiosk.completionSubtitleHint')}
            />
          </Field>
        </div>
      </section>

      {/* ═════════════════ 支付方式 ═════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('kiosk.sectionPayment')}</SectionTitle>
        <p className="text-xs text-gray-400 -mt-3">{t('kiosk.paymentHint')}</p>

        <div className="flex flex-wrap gap-3">
          {PAYMENT_OPTIONS.map(m => (
            <label key={m} className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none border border-gray-200 rounded-lg px-3 py-2">
              <input
                type="checkbox" className="rounded"
                checked={kiosk.enabledPaymentMethods.includes(m)}
                onChange={e => {
                  const next = e.target.checked
                    ? [...kiosk.enabledPaymentMethods, m]
                    : kiosk.enabledPaymentMethods.filter((x: string) => x !== m)
                  patch('enabledPaymentMethods', next)
                }}
              />
              {t(`kiosk.payment.${m.toLowerCase()}`)}
            </label>
          ))}
        </div>
      </section>

      {/* ═════════════════ QR 码配置 ═════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('kiosk.sectionQr')}</SectionTitle>

        <div className="grid grid-cols-2 gap-4">
          <Field label={t('kiosk.qrErrorCorrection')}>
            <select
              className="input w-40 font-mono text-sm"
              value={kiosk.qrErrorCorrectionLevel}
              onChange={e => patch('qrErrorCorrectionLevel', e.target.value)}
            >
              {QR_EC_OPTIONS.map(l => (
                <option key={l} value={l}>{l} ({t(`kiosk.qrEc.${l.toLowerCase()}`)})</option>
              ))}
            </select>
          </Field>

          <Field label={t('kiosk.confirmationQrSize')}>
            <div className="flex items-center gap-1">
              <input
                className="input w-24 font-mono text-sm"
                type="number" min={100} max={400}
                value={kiosk.confirmationQrSizeDp}
                onChange={e => patch('confirmationQrSizeDp', Number(e.target.value))}
              />
              <span className="text-xs text-gray-400">dp</span>
            </div>
          </Field>
        </div>
      </section>

      {/* ═════════════════ 超时与开关 ═════════════════ */}
      <section className="space-y-5 border-t border-gray-200 pt-8">
        <SectionTitle>{t('kiosk.sectionBehavior')}</SectionTitle>

        <div className="grid grid-cols-2 gap-4">
          <Field label={t('kiosk.autoReturnSeconds')}>
            <div className="flex items-center gap-1">
              <input
                className="input w-24 font-mono text-sm"
                type="number" min={5} max={300}
                value={kiosk.autoReturnSeconds}
                onChange={e => patch('autoReturnSeconds', Number(e.target.value))}
              />
              <span className="text-xs text-gray-400">s</span>
            </div>
          </Field>
        </div>

        <div className="space-y-3">
          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={kiosk.showDigitalReceiptOption}
              onChange={e => patch('showDigitalReceiptOption', e.target.checked)}
            />
            {t('kiosk.showDigitalReceiptOption')}
          </label>

          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={kiosk.showPromotionBanner}
              onChange={e => patch('showPromotionBanner', e.target.checked)}
            />
            {t('kiosk.showPromotionBanner')}
          </label>

          {kiosk.showPromotionBanner && (
            <Field label={t('kiosk.promotionBannerUrl')}>
              <input
                className="input w-full font-mono text-sm"
                type="text"
                value={kiosk.promotionBannerUrl ?? ''}
                onChange={e => patch('promotionBannerUrl', e.target.value || null)}
                placeholder="https://example.com/banner.jpg"
              />
            </Field>
          )}

          <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
            <input
              type="checkbox" className="rounded"
              checked={kiosk.accessibilityEnabled}
              onChange={e => patch('accessibilityEnabled', e.target.checked)}
            />
            {t('kiosk.accessibilityEnabled')}
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

      <KioskPreview kiosk={kiosk} />
      </div>
    </div>
  )
}
