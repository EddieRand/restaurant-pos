import axios from 'axios'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'
import { aiInsightApi, type AiActionPriority, type AiObservationSeverity, type OperatingInsightResponse } from '../api/ai'
import { fmtMoney } from '../api/reports'
import { getAiCopy, interpolate, prettifyEvidenceKey } from '../i18n/ai'

type ErrorKind = 'unauthorized' | 'quota' | 'rateLimited' | 'unavailable' | 'timeout' | 'generic'

function classifyError(error: unknown): ErrorKind {
  if (!axios.isAxiosError(error)) return 'generic'
  const providerCode = typeof error.response?.data === 'object' && error.response.data !== null && 'code' in error.response.data
    ? String(error.response.data.code)
    : undefined
  if (providerCode === 'AI_AUTH_FAILED') return 'unauthorized'
  if (providerCode === 'AI_QUOTA_EXCEEDED') return 'quota'
  if (providerCode === 'AI_RATE_LIMITED') return 'rateLimited'
  if (providerCode === 'AI_PROVIDER_UNAVAILABLE' || providerCode === 'AI_NOT_CONFIGURED') return 'unavailable'
  if (providerCode === 'AI_TIMEOUT') return 'timeout'
  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') return 'timeout'
  if (error.response?.status === 401) return 'unauthorized'
  if (error.response?.status === 402) return 'quota'
  if (error.response?.status === 429) return 'rateLimited'
  if (error.response?.status === 503) return 'unavailable'
  if (error.response?.status === 504) return 'timeout'
  return 'generic'
}

const observationStyles: Record<AiObservationSeverity, string> = {
  positive: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  warning: 'border-amber-200 bg-amber-50 text-amber-700',
  neutral: 'border-slate-200 bg-slate-50 text-slate-700',
}

const priorityStyles: Record<AiActionPriority, string> = {
  high: 'bg-red-100 text-red-700',
  medium: 'bg-amber-100 text-amber-700',
  low: 'bg-blue-100 text-blue-700',
}

export default function AiOperatingInsightCard({ fromMs, toMs }: { fromMs: number; toMs: number }) {
  const { i18n } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const copy = useMemo(() => getAiCopy(i18n.language), [i18n.language])
  const [insight, setInsight] = useState<OperatingInsightResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ErrorKind | null>(null)

  useEffect(() => {
    setInsight(null)
    setError(null)
  }, [fromMs, toMs])

  async function generate() {
    setLoading(true)
    setError(null)
    try {
      setInsight(await aiInsightApi.generate({ fromMs, toMs, locale: i18n.language }))
    } catch (requestError) {
      setError(classifyError(requestError))
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="card mb-8 overflow-hidden border border-indigo-100 bg-gradient-to-br from-white via-white to-indigo-50/60">
      <div className="p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="max-w-2xl">
            <div className="mb-2 flex items-center gap-2 text-xs font-semibold tracking-[0.16em] text-indigo-500">
              <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-indigo-100 text-base" aria-hidden="true">✦</span>
              {copy.eyebrow}
            </div>
            <h2 className="text-lg font-semibold text-gray-900">{copy.title}</h2>
            <p className="mt-1 text-sm leading-6 text-gray-500">{copy.description}</p>
          </div>
          <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
            <button
              type="button"
              className="btn-secondary shrink-0"
              onClick={() => navigate('/ai', { state: { fromMs, toMs, fromRoute: location.pathname } })}
            >
              {insight ? copy.continueInWorkspace : copy.openWorkspace} →
            </button>
            <button
              type="button"
              className="btn-primary shrink-0 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={loading}
              onClick={generate}
            >
              {loading ? copy.generating : insight ? copy.regenerate : error ? copy.retry : copy.generate}
            </button>
          </div>
        </div>

        {error && (
          <div role="alert" className="mt-5 flex items-start justify-between gap-4 rounded-lg border border-red-100 bg-red-50 p-4 text-sm text-red-700">
            <span>{copy.errors[error]}</span>
            <button type="button" onClick={generate} className="shrink-0 font-semibold underline underline-offset-2">{copy.retry}</button>
          </div>
        )}

        {insight && (
          <div className="mt-6 space-y-6">
            <div>
              <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
                <div>
                  <h3 className="text-xl font-semibold text-gray-900">{insight.headline}</h3>
                  <p className="mt-2 max-w-4xl text-sm leading-6 text-gray-600">{insight.summary}</p>
                </div>
                <span className="text-xs text-gray-400">
                  {interpolate(copy.generatedAt, {
                    time: new Date(insight.generatedAt).toLocaleString(i18n.language),
                    model: insight.model,
                  })}
                </span>
              </div>
            </div>

            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-400">{copy.snapshot}</p>
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                {[
                  [copy.orders, String(insight.snapshot.orderCount)],
                  [copy.revenue, fmtMoney(insight.snapshot.netRevenueMinorUnit)],
                  [copy.avgOrder, fmtMoney(insight.snapshot.averageOrderValueMinorUnit)],
                  [copy.guests, String(insight.snapshot.guestCount)],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-lg border border-gray-100 bg-white/80 p-3">
                    <p className="text-xs text-gray-400">{label}</p>
                    <p className="mt-1 text-lg font-semibold tabular-nums text-gray-900">{value}</p>
                  </div>
                ))}
              </div>
            </div>

            {insight.observations.length > 0 && (
              <div>
                <h4 className="mb-3 text-sm font-semibold text-gray-900">{copy.observations}</h4>
                <div className="grid gap-3 lg:grid-cols-3">
                  {insight.observations.map((observation, index) => (
                    <article key={`${observation.title}-${index}`} className={`rounded-lg border p-4 ${observationStyles[observation.severity]}`}>
                      <h5 className="text-sm font-semibold">{observation.title}</h5>
                      <p className="mt-1 text-xs leading-5 opacity-90">{observation.detail}</p>
                      {observation.evidenceKeys.length > 0 && (
                        <p className="mt-3 text-[11px] opacity-70">
                          {copy.evidence}: {observation.evidenceKeys.map(key => copy.evidenceLabels[key] ?? prettifyEvidenceKey(key)).join(' · ')}
                        </p>
                      )}
                    </article>
                  ))}
                </div>
              </div>
            )}

            {insight.actions.length > 0 && (
              <div>
                <h4 className="mb-3 text-sm font-semibold text-gray-900">{copy.actions}</h4>
                <div className="space-y-2">
                  {insight.actions.map((action, index) => (
                    <article key={`${action.title}-${index}`} className="flex gap-3 rounded-lg border border-gray-100 bg-white/80 p-4">
                      <span className={`mt-0.5 h-fit shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold ${priorityStyles[action.priority]}`}>{copy.priorityLabels[action.priority]}</span>
                      <div>
                        <h5 className="text-sm font-semibold text-gray-800">{action.title}</h5>
                        <p className="mt-1 text-xs leading-5 text-gray-500">{action.reason}</p>
                      </div>
                    </article>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
      <div className="border-t border-indigo-100 bg-indigo-50/50 px-6 py-3 text-xs text-indigo-700">{copy.disclaimer}</div>
    </section>
  )
}
