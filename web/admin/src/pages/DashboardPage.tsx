import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../api/client'
import { fmtMoney, orderApi, reportApi, type TopItem } from '../api/reports'
import { ingredientApi, type Ingredient, paymentMethodApi, type PaymentMethodConfig } from '../api/admin'
import DatePicker from '../components/DatePicker'
import AiOperatingInsightCard from '../components/AiOperatingInsightCard'
import { useVisiblePolling } from '../hooks/useVisiblePolling'

// ── Date range presets (mirrors ReportPage.tsx) ──────────────────────────────
type Preset = 'today' | 'yesterday' | 'week' | 'month' | 'custom'

function rangeFor(preset: Preset, customFrom: string, customTo: string): [number, number] {
  const now = new Date()
  const sod = (d: Date) => { d.setHours(0, 0, 0, 0); return d.getTime() }
  const eod = (d: Date) => { d.setHours(23, 59, 59, 999); return d.getTime() }

  if (preset === 'today') return [sod(new Date()), eod(new Date())]
  if (preset === 'yesterday') {
    const y = new Date(); y.setDate(y.getDate() - 1)
    return [sod(y), eod(new Date(y))]
  }
  if (preset === 'week') {
    const w = new Date(); w.setDate(w.getDate() - 6)
    return [sod(w), eod(new Date())]
  }
  if (preset === 'month') {
    const m = new Date(now.getFullYear(), now.getMonth(), 1)
    return [sod(m), eod(new Date())]
  }
  const f = customFrom ? new Date(customFrom).getTime() : sod(new Date())
  const t = customTo ? eod(new Date(customTo)) : eod(new Date())
  return [f, t]
}

interface ShiftReport {
  orderCount: number
  netRevenueMinorUnit: number
  grossRevenueMinorUnit: number
  totalDiscountMinorUnit: number
  totalGuestCount: number
  averageOrderValueMinorUnit: number
  averageSpendPerGuestMinorUnit: number
  paymentMethodBreakdown: Record<string, number>
}

const fmt = fmtMoney

function calcPctChange(current: number, previous: number): number | null {
  if (previous === 0) return current > 0 ? null : 0
  return Math.round(((current - previous) / previous) * 1000) / 10
}

export default function DashboardPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [report, setReport] = useState<ShiftReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [lowStock, setLowStock] = useState<Ingredient[]>([])
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodConfig[]>([])
  useEffect(() => { paymentMethodApi.list().then(d => setPaymentMethods(Array.isArray(d) ? d : [])).catch(() => {}) }, [])
  function paymentLabel(code: string) {
    return paymentMethods.find(p => p.code === code)?.displayName
      ?? t(`dashboard.paymentLabels.${code}`, { defaultValue: code })
  }

  // Date range selector for the main stats
  const [preset, setPreset] = useState<Preset>('today')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')

  // Needs-attention widgets
  const [pendingQrCount, setPendingQrCount] = useState(0)
  const [waiterCallCount, setWaiterCallCount] = useState(0)
  const [topItems, setTopItems] = useState<TopItem[]>([])

  // Week-over-week comparison
  const [thisWeek, setThisWeek] = useState<ShiftReport | null>(null)
  const [lastWeek, setLastWeek] = useState<ShiftReport | null>(null)

  useEffect(() => {
    ingredientApi.list().then(items => {
      setLowStock(items.filter(i => i.currentStock < i.safetyStock).sort((a, b) => (a.currentStock - a.safetyStock) - (b.currentStock - b.safetyStock)))
    })
  }, [])

  const loadReport = useCallback(() => {
    const [from, to] = rangeFor(preset, customFrom, customTo)
    apiClient
      .get<ShiftReport>(`/admin/reports/shift?from=${from}&to=${to}`)
      .then((r) => { setReport(r.data); setError(null) })
      .catch(() => setError(t('dashboard.errorLoad')))
      .finally(() => setLoading(false))
  }, [t, preset, customFrom, customTo])
  useEffect(() => { setLoading(true); setError(null) }, [preset, customFrom, customTo])
  useVisiblePolling(loadReport)

  // Needs-attention: pending QR confirmations, waiter calls, today's top items
  useEffect(() => {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const from = today.getTime()
    const to = Date.now()

    orderApi.list({ status: 'PENDING_CONFIRMATION', page: 1, pageSize: 1 })
      .then(r => setPendingQrCount(r.total))
      .catch(() => {})

    apiClient.get<unknown[]>('/waiter-calls')
      .then(r => setWaiterCallCount(Array.isArray(r.data) ? r.data.length : 0))
      .catch(() => {})

    reportApi.topItems({ from, to, limit: 5 })
      .then(setTopItems)
      .catch(() => {})
  }, [])

  // Fetch this week & last week for comparison
  useEffect(() => {
    const now = new Date()
    const dayOfWeek = now.getDay() || 7  // Monday=1..Sunday=7
    const thisMonday = new Date(now)
    thisMonday.setDate(now.getDate() - dayOfWeek + 1)
    thisMonday.setHours(0, 0, 0, 0)
    const thisFrom = thisMonday.getTime()
    const thisTo = now.getTime()

    const lastMonday = new Date(thisMonday)
    lastMonday.setDate(thisMonday.getDate() - 7)
    const lastSunday = new Date(thisMonday)
    lastSunday.setDate(thisMonday.getDate() - 1)
    lastSunday.setHours(23, 59, 59, 999)
    const lastFrom = lastMonday.getTime()
    const lastTo = lastSunday.getTime()

    apiClient.get<ShiftReport>(`/admin/reports/shift?from=${thisFrom}&to=${thisTo}`)
      .then(r => setThisWeek(r.data))
      .catch(() => {})

    apiClient.get<ShiftReport>(`/admin/reports/shift?from=${lastFrom}&to=${lastTo}`)
      .then(r => setLastWeek(r.data))
      .catch(() => {})
  }, [])

  const todayLabel = new Date().toLocaleDateString(i18n.language, { month: 'long', day: 'numeric', weekday: 'long' })

  function exportCsv() {
    if (!report) return
    const rows = [
      [t('reports.rangeFrom'), new Date(rangeFor(preset, customFrom, customTo)[0]).toLocaleString()],
      [t('reports.rangeTo'), new Date(rangeFor(preset, customFrom, customTo)[1]).toLocaleString()],
      [t('reports.orderCount'), String(report.orderCount)],
      [t('reports.gross'), fmt(report.grossRevenueMinorUnit)],
      [t('reports.netRevenue'), fmt(report.netRevenueMinorUnit)],
      [t('reports.discount'), fmt(report.totalDiscountMinorUnit)],
      [t('reports.guestCount'), String(report.totalGuestCount)],
      [t('reports.avgOrder'), fmt(report.averageOrderValueMinorUnit)],
      [t('reports.avgGuest'), fmt(report.averageSpendPerGuestMinorUnit)],
      [],
      [t('reports.paymentBreakdown'), ''],
      ...Object.entries(report.paymentMethodBreakdown).map(([k, v]) => [
        paymentLabel(k), fmt(v),
      ]),
    ]
    const csv = rows.map(r => r.join(',')).join('\n')
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = `revenue-report-${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url)
  }

  return (
    <div className="p-8 w-full">
      {/* Header */}
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">{t('dashboard.title')}</h1>
          <p className="mt-1 text-sm text-gray-500">{todayLabel}</p>
        </div>

        {/* Date range selector for the stats below */}
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex gap-1 bg-gray-100 p-1 rounded-lg">
            {([
              ['today', t('reports.today')],
              ['yesterday', t('reports.yesterday')],
              ['week', t('reports.last7')],
              ['month', t('reports.thisMonth')],
              ['custom', t('reports.custom')],
            ] as [Preset, string][]).map(([key, label]) => (
              <button
                key={key}
                onClick={() => setPreset(key)}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  preset === key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
          {preset === 'custom' && (
            <div className="flex items-center gap-2">
              <DatePicker value={customFrom} onChange={setCustomFrom} placeholder={t('reports.rangeFrom')} />
              <span className="text-gray-400 text-xs">{t('reports.rangeTo')}</span>
              <DatePicker value={customTo} onChange={setCustomTo} placeholder={t('reports.rangeTo')} />
            </div>
          )}

          <button
            onClick={exportCsv}
            disabled={!report || loading}
            className="btn-secondary text-xs disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5"
          >
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
            {t('dashboard.exportCsv')}
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-6 p-4 rounded-lg bg-amber-50 border border-amber-100 text-sm text-amber-800">
          {error}
        </div>
      )}

      {/* Stats grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          label={t('dashboard.netRevenue')}
          value={loading ? '—' : fmt(report?.netRevenueMinorUnit ?? 0)}
          sub={t('dashboard.netRevenueSub')}
          accent
        />
        <StatCard
          label={t('dashboard.orderCount')}
          value={loading ? '—' : String(report?.orderCount ?? 0)}
          sub={t('dashboard.orderCountSub')}
        />
        <StatCard
          label={t('dashboard.guestCount')}
          value={loading ? '—' : String(report?.totalGuestCount ?? 0)}
          sub={t('dashboard.guestCountSub')}
        />
        <StatCard
          label={t('dashboard.avgOrder')}
          value={loading ? '—' : fmt(report?.averageOrderValueMinorUnit ?? 0)}
          sub={t('dashboard.avgOrderSub')}
        />
      </div>

      {/* Secondary stats */}
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        <StatCard label={t('dashboard.discount')} value={loading ? '—' : fmt(report?.totalDiscountMinorUnit ?? 0)} />
        <StatCard label={t('dashboard.gross')} value={loading ? '—' : fmt(report?.grossRevenueMinorUnit ?? 0)} />
        <StatCard label={t('dashboard.avgGuest')} value={loading ? '—' : fmt(report?.averageSpendPerGuestMinorUnit ?? 0)} />
      </div>

      <AiOperatingInsightCard
        fromMs={rangeFor(preset, customFrom, customTo)[0]}
        toMs={rangeFor(preset, customFrom, customTo)[1]}
      />

      {/* Week vs Last Week comparison */}
      {thisWeek && lastWeek && (
        <div className="card p-6 mb-8 border-l-4 border-brand-400">
          <div className="flex items-center gap-2 mb-4">
            <svg className="w-5 h-5 text-brand-500" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M12 7a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0V8.414l-4.293 4.293a1 1 0 01-1.414 0L8 10.414l-4.293 4.293a1 1 0 01-1.414-1.414l5-5a1 1 0 011.414 0L11 10.586 14.586 7H12z" clipRule="evenodd" />
            </svg>
            <h2 className="text-sm font-semibold text-gray-900">{t('dashboard.weekVsLast')}</h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <WoWCard
              label={t('dashboard.netRevenueWoW')}
              current={thisWeek.netRevenueMinorUnit}
              previous={lastWeek.netRevenueMinorUnit}
              isMoney
            />
            <WoWCard
              label={t('dashboard.orderCountWoW')}
              current={thisWeek.orderCount}
              previous={lastWeek.orderCount}
            />
            <WoWCard
              label={t('dashboard.guestCountWoW')}
              current={thisWeek.totalGuestCount}
              previous={lastWeek.totalGuestCount}
            />
          </div>
        </div>
      )}

      {/* Needs attention */}
      <NeedsAttentionCard
        pendingQrCount={pendingQrCount}
        waiterCallCount={waiterCallCount}
        lowStockCount={lowStock.length}
        onViewQrOrders={() => navigate('/orders?status=PENDING_CONFIRMATION')}
        onViewLowStock={() => navigate('/inventory')}
      />

      {/* Low stock alert */}
      <LowStockCard items={lowStock} onViewAll={() => navigate('/inventory')} />

      {/* Top selling items today */}
      <TopItemsCard items={topItems} language={i18n.language} />

      {/* Payment breakdown */}
      {report && Object.keys(report.paymentMethodBreakdown).length > 0 && (
        <div className="card p-6">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">{t('dashboard.paymentBreakdown')}</h2>
          <div className="space-y-3">
            {Object.entries(report.paymentMethodBreakdown).map(([method, amount]) => {
              const total = Object.values(report.paymentMethodBreakdown).reduce((a, b) => a + b, 0)
              const pct = total > 0 ? Math.round((amount / total) * 100) : 0
              return (
                <div key={method} className="flex items-center gap-3">
                  <span className="text-sm text-gray-600 w-16 flex-shrink-0">
                    {paymentLabel(method)}
                  </span>
                  <div className="flex-1 h-2 bg-gray-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-brand-500 rounded-full transition-all duration-500"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <span className="text-sm font-medium text-gray-700 w-20 text-right">{fmt(amount)}</span>
                  <span className="text-xs text-gray-400 w-8 text-right">{pct}%</span>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {!loading && report?.orderCount === 0 && (
        <div className="text-center py-16 text-gray-400">
          <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
          </svg>
          <p className="text-sm">{t('dashboard.noOrders')}</p>
        </div>
      )}
    </div>
  )
}

// ── Week-over-Week comparison card ─────────────────────────────────────────
function WoWCard({ label, current, previous, isMoney }: {
  label: string
  current: number
  previous: number
  isMoney?: boolean
}) {
  const { t } = useTranslation()
  const pct = calcPctChange(current, previous)

  return (
    <div className="bg-gray-50 rounded-lg p-4">
      <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{label}</p>
      <p className="text-xl font-semibold tabular-nums text-gray-900">
        {isMoney ? fmt(current) : current}
      </p>
      <div className="flex items-center gap-1.5 mt-1">
        {pct != null ? (
          <>
            <span className={`text-xs font-semibold ${pct >= 0 ? 'text-red-500' : 'text-green-500'}`}>
              {pct >= 0 ? '↑' : '↓'} {Math.abs(pct)}%
            </span>
            <span className="text-xs text-gray-400">{t('dashboard.vsLastWeek')}</span>
          </>
        ) : (
          <span className="text-xs text-gray-400">{t('dashboard.noComparison')}</span>
        )}
      </div>
      <p className="text-xs text-gray-400 mt-0.5">
        {t('dashboard.vsLastWeek')}: {isMoney ? fmt(previous) : previous}
      </p>
    </div>
  )
}

function LowStockCard({ items, onViewAll }: { items: Ingredient[]; onViewAll: () => void }) {
  const { t } = useTranslation()
  if (items.length === 0) return null

  const critical = items.filter(i => i.currentStock <= 0)

  return (
    <div className="card p-6 mb-8 border-l-4 border-orange-400">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-2">
          <svg className="w-5 h-5 text-orange-500 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
          <div>
            <h2 className="text-sm font-semibold text-gray-900">
              {t('dashboard.lowStockTitle')}
              <span className="ms-2 inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-orange-100 text-orange-700">{items.length}</span>
            </h2>
            <p className="text-xs text-gray-400 mt-0.5">{t('dashboard.lowStockSub')}</p>
          </div>
        </div>
        <button className="text-xs text-brand-600 hover:text-brand-800 font-medium" onClick={onViewAll}>
          {t('dashboard.lowStockViewAll')} →
        </button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100">
              <th className="text-start pb-2 font-medium text-gray-500 text-xs">{t('dashboard.lowStockColName')}</th>
              <th className="text-end pb-2 font-medium text-gray-500 text-xs">{t('dashboard.lowStockColCurrent')}</th>
              <th className="text-end pb-2 font-medium text-gray-500 text-xs">{t('dashboard.lowStockColSafety')}</th>
              <th className="text-end pb-2 font-medium text-gray-500 text-xs">{t('dashboard.lowStockColGap')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {items.map(ing => {
              const isCritical = ing.currentStock <= 0
              const gap = ing.safetyStock - ing.currentStock
              return (
                <tr key={ing.id}>
                  <td className="py-2.5">
                    <div className="flex items-center gap-2">
                      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${isCritical ? 'bg-red-500' : 'bg-orange-400'}`} />
                      <span className="font-medium text-gray-800">{ing.name}</span>
                      <span className="text-xs text-gray-400">{ing.category}</span>
                    </div>
                  </td>
                  <td className="py-2.5 text-end font-mono">
                    <span className={isCritical ? 'text-red-600 font-semibold' : 'text-orange-500 font-semibold'}>
                      {ing.currentStock} {ing.unit}
                    </span>
                  </td>
                  <td className="py-2.5 text-end font-mono text-gray-400 text-xs">{ing.safetyStock} {ing.unit}</td>
                  <td className="py-2.5 text-end font-mono">
                    <span className={`text-xs font-medium px-1.5 py-0.5 rounded ${isCritical ? 'bg-red-100 text-red-600' : 'bg-orange-100 text-orange-600'}`}>
                      -{Math.round(gap * 100) / 100} {ing.unit}
                    </span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {(critical.length > 0) && (
        <p className="mt-3 text-xs text-red-500 font-medium">
          ⚠ {t('dashboard.criticalStockAlert', { count: critical.length })}
        </p>
      )}
    </div>
  )
}

function parseItemName(names: string, lang = 'zh-CN'): string {
  try {
    const m = JSON.parse(names)
    const shortLang = lang.split('-')[0]
    return m[lang] ?? m[shortLang] ?? m['zh-CN'] ?? m.zh ?? m['en-US'] ?? m.en ?? names
  } catch {
    return names
  }
}

// ── Needs Attention card ────────────────────────────────────────────────────
function NeedsAttentionCard({ pendingQrCount, waiterCallCount, lowStockCount, onViewQrOrders, onViewLowStock }: {
  pendingQrCount: number
  waiterCallCount: number
  lowStockCount: number
  onViewQrOrders: () => void
  onViewLowStock: () => void
}) {
  const items: { count: number; label: string; sub: string; onClick?: () => void; color: 'blue' | 'purple' | 'orange' }[] = [
    { count: pendingQrCount, label: '待确认的扫码点单', sub: '需要确认或拒绝顾客提交的订单', onClick: onViewQrOrders, color: 'blue' as const },
    { count: waiterCallCount, label: '未处理的呼叫', sub: '顾客发起的服务呼叫尚未应答', color: 'purple' as const },
    { count: lowStockCount, label: '库存预警', sub: '低于安全库存的食材', onClick: onViewLowStock, color: 'orange' as const },
  ].filter(i => i.count > 0)

  if (items.length === 0) return null

  const colorClasses: Record<string, string> = {
    blue: 'bg-blue-100 text-blue-700',
    purple: 'bg-purple-100 text-purple-700',
    orange: 'bg-orange-100 text-orange-700',
  }

  return (
    <div className="card p-6 mb-8 border-l-4 border-red-400">
      <div className="flex items-center gap-2 mb-4">
        <svg className="w-5 h-5 text-red-500" viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
        </svg>
        <h2 className="text-sm font-semibold text-gray-900">待办事项</h2>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {items.map(item => {
          const Tag = item.onClick ? 'button' : 'div'
          return (
            <Tag
              key={item.label}
              onClick={item.onClick}
              className={`text-start bg-gray-50 rounded-lg p-4 ${item.onClick ? 'hover:bg-gray-100 transition-colors' : ''}`}
            >
              <div className="flex items-center gap-2 mb-1">
                <span className={`inline-flex min-w-[1.5rem] justify-center px-1.5 py-0.5 rounded-full text-xs font-semibold ${colorClasses[item.color]}`}>
                  {item.count}
                </span>
                <span className="text-sm font-medium text-gray-800">{item.label}</span>
              </div>
              <p className="text-xs text-gray-400">{item.sub}</p>
            </Tag>
          )
        })}
      </div>
    </div>
  )
}

// ── Top selling items today ─────────────────────────────────────────────────
function TopItemsCard({ items, language }: { items: TopItem[]; language: string }) {
  if (items.length === 0) return null

  const maxQty = Math.max(...items.map(i => i.quantity))

  return (
    <div className="card p-6 mb-8">
      <div className="flex items-center gap-2 mb-4">
        <svg className="w-5 h-5 text-brand-500" viewBox="0 0 20 20" fill="currentColor">
          <path d="M2 11a1 1 0 011-1h2a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1v-5zM8 7a1 1 0 011-1h2a1 1 0 011 1v9a1 1 0 01-1 1H9a1 1 0 01-1-1V7zM14 4a1 1 0 011-1h2a1 1 0 011 1v12a1 1 0 01-1 1h-2a1 1 0 01-1-1V4z" />
        </svg>
        <h2 className="text-sm font-semibold text-gray-900">今日热销</h2>
      </div>
      <div className="space-y-3">
        {items.map((item, idx) => {
          const pct = maxQty > 0 ? Math.round((item.quantity / maxQty) * 100) : 0
          return (
            <div key={item.menuItemId} className="flex items-center gap-3">
              <span className="w-5 text-xs font-semibold text-gray-400 text-right flex-shrink-0">{idx + 1}</span>
              <span className="text-sm text-gray-700 w-32 flex-shrink-0 truncate">{parseItemName(item.names, language)}</span>
              <div className="flex-1 h-2 bg-gray-100 rounded-full overflow-hidden">
                <div className="h-full bg-brand-500 rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
              </div>
              <span className="text-sm font-medium text-gray-700 w-12 text-right flex-shrink-0">{item.quantity} 份</span>
              <span className="text-sm font-medium text-gray-500 w-20 text-right flex-shrink-0">{fmt(item.revenueMinorUnit)}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function StatCard({ label, value, sub, accent }: { label: string; value: string; sub?: string; accent?: boolean }) {
  return (
    <div className={`stat-card ${accent ? 'ring-1 ring-brand-200' : ''}`}>
      <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{label}</p>
      <p className={`text-2xl font-semibold tabular-nums ${accent ? 'text-brand-600' : 'text-gray-900'}`}>
        {value}
      </p>
      {sub && <p className="text-xs text-gray-400">{sub}</p>}
    </div>
  )
}
