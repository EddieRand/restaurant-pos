import { useEffect, useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import DatePicker from '../components/DatePicker'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Area, AreaChart,
} from 'recharts'
import {
  reportApi, shiftApi, fmtMoney,
  type TrendReport,
  type CategorySales, type ItemSales, type OrderTypeSales, type StaffReport, type HourlySales,
  type PaymentMethodReport, type TaxReportSummary, type ModifierSales, type CashierShift,
} from '../api/reports'
import { timecardApi, type HoursReportResponse } from '../api/admin'
import { ErrorBoundary } from '../components/ErrorBoundary'
import { parseName } from '../api/menu'

// ── Date range presets ────────────────────────────────────────────────────────
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

const BRAND = '#FF5C00'
const BRAND_LIGHT = '#FF8533'

type ReportTab = 'cashierShift' | 'trend' | 'peak' | 'products' | 'staff' | 'channels' | 'payments' | 'tax' | 'modifiers' | 'hours'

// ══════════════════════════════════════════════════════════════════════════════
export default function ReportPage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<ReportTab>('cashierShift')

  const tabs: { key: ReportTab; label: string }[] = useMemo(() => [
    { key: 'cashierShift', label: t('reports.tabCashierShift') },
    { key: 'trend', label: t('reports.tabTrend') },
    { key: 'peak', label: t('reports.tabPeak') },
    { key: 'products', label: t('reports.tabProducts') },
    { key: 'staff', label: t('reports.tabStaff') },
    { key: 'channels', label: t('reports.tabChannels') },
    { key: 'payments', label: t('reports.tabPayments') },
    { key: 'tax', label: t('reports.tabTax') },
    { key: 'modifiers', label: t('reports.tabModifiers') },
    { key: 'hours', label: t('reports.tabHours') },
  ], [t])

  return (
    <div className="p-8 w-full">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-gray-900">{t('reports.title')}</h1>
        <p className="mt-1 text-sm text-gray-500">{t('reports.subtitle')}</p>
      </div>

      {/* Tab bar */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit mb-6 flex-wrap">
        {tabs.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-colors whitespace-nowrap ${
              activeTab === tab.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab content — each wrapped in an ErrorBoundary so a single broken tab
          cannot white-screen the whole page */}
      <ErrorBoundary label={activeTab}>
        {activeTab === 'cashierShift' && <CashierShiftTab />}
        {activeTab === 'trend' && <TrendTab />}
        {activeTab === 'peak' && <PeakHoursTab />}
        {activeTab === 'products' && <ProductsTab />}
        {activeTab === 'staff' && <StaffTab />}
        {activeTab === 'channels' && <ChannelsTab />}
        {activeTab === 'payments' && <PaymentsTab />}
        {activeTab === 'tax' && <TaxTab />}
        {activeTab === 'modifiers' && <ModifiersTab />}
        {activeTab === 'hours' && <HoursTab />}
      </ErrorBoundary>
    </div>
  )
}
// ── Trend Tab ────────────────────────────────────────────────────────────────
type TrendDays = 7 | 15 | 30

function TrendTab() {
  const { t } = useTranslation()
  const [days, setDays] = useState<TrendDays>(7)
  const [trend, setTrend] = useState<TrendReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const to = new Date()
    const from = new Date()
    from.setDate(to.getDate() - days)
    const fromStr = from.toISOString().slice(0, 10)
    const toStr = to.toISOString().slice(0, 10)

    setLoading(true); setError(null)
    reportApi.trend({ fromDate: fromStr, toDate: toStr, granularity: 'day' })
      .then(d => setTrend(Array.isArray(d?.dataPoints) ? d : null))
      .catch(() => setError(t('reports.errorLoad')))
      .finally(() => setLoading(false))
  }, [days, t])

  const chartData = useMemo(() => {
    if (!trend || !Array.isArray(trend.dataPoints)) return []
    return trend.dataPoints.map(p => ({
      date: p.date.slice(5),  // MM-DD
      revenue: p.netRevenueMinorUnit,
      orders: p.orderCount,
      guests: p.guestCount,
    }))
  }, [trend])

  const summary = trend?.summary

  return (
    <>
      {/* Day selector */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit mb-6">
        {([7, 15, 30] as TrendDays[]).map(d => (
          <button
            key={d}
            onClick={() => setDays(d)}
            className={`px-4 py-1.5 rounded-md text-xs font-medium transition-colors ${
              days === d ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {t(`reports.trendDays${d}`)}
          </button>
        ))}
      </div>

      {loading && (
        <div className="flex items-center justify-center py-16 text-gray-400">
          <svg className="w-5 h-5 animate-spin mr-2" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <span className="text-sm">Loading…</span>
        </div>
      )}

      {error && (
        <div className="mb-6 p-4 rounded-lg bg-amber-50 border border-amber-100 text-sm text-amber-800">{error}</div>
      )}

      {!loading && !trend && (
        <div className="text-center py-16 text-gray-400">
          <p className="text-sm">{t('reports.trendNoData')}</p>
        </div>
      )}

      {trend && !loading && (
        <>
          {/* Summary cards */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <StatCard
              label={t('reports.trendTotal')}
              value={fmtMoney(summary?.totalNetRevenue ?? 0)}
              accent
            />
            <StatCard
              label={t('reports.trendAvgDaily')}
              value={summary ? fmtMoney(Math.round(summary.totalNetRevenue / days)) : '—'}
            />
            <StatCard
              label={t('reports.trendOrders')}
              value={String(summary?.totalOrderCount ?? 0)}
            />
            <StatCard
              label={t('reports.trendGuests')}
              value={String(summary?.totalGuestCount ?? 0)}
            />
          </div>

          {/* Growth badge */}
          {summary?.growthFromPrevious != null && (
            <div className="mb-6 flex items-center gap-2">
              <span className="text-sm text-gray-500">{t('reports.trendGrowth')}</span>
              <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                summary.growthFromPrevious >= 0
                  ? 'bg-red-50 text-red-600'
                  : 'bg-green-50 text-green-600'
              }`}>
                {summary.growthFromPrevious >= 0 ? '↑' : '↓'} {Math.abs(summary.growthFromPrevious).toFixed(1)}%
              </span>
            </div>
          )}

          {/* Area chart */}
          {chartData.length > 0 && (
            <div className="card p-5 mb-6">
              <h3 className="text-sm font-semibold text-gray-700 mb-4">{t('reports.tabTrend')}</h3>
              <ResponsiveContainer width="100%" height={320}>
                <AreaChart data={chartData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                  <defs>
                    <linearGradient id="revGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={BRAND} stopOpacity={0.3} />
                      <stop offset="95%" stopColor={BRAND} stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" vertical={false} />
                  <XAxis
                    dataKey="date"
                    tick={{ fontSize: 11, fill: '#9ca3af' }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: '#9ca3af' }}
                    axisLine={false}
                    tickLine={false}
                    tickFormatter={(v: number) => {
                      if (v >= 10000) return `${(v / 10000).toFixed(0)}w`
                      return fmtMoney(v)
                    }}
                  />
                  <Tooltip
                    formatter={(value: number, name: string) => {
                      if (name === 'revenue') return [fmtMoney(value), t('reports.netRevenue')]
                      return [value, name]
                    }}
                    contentStyle={{ borderRadius: 8, border: '1px solid #e5e7eb', fontSize: 12 }}
                  />
                  <Area
                    type="monotone"
                    dataKey="revenue"
                    stroke={BRAND}
                    strokeWidth={2.5}
                    fill="url(#revGradient)"
                    dot={{ r: 3, fill: BRAND, strokeWidth: 0 }}
                    activeDot={{ r: 5, fill: BRAND, stroke: '#fff', strokeWidth: 2 }}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* Orders trend */}
          {chartData.length > 0 && (
            <div className="card p-5">
              <h3 className="text-sm font-semibold text-gray-700 mb-4">{t('reports.orderCount')}</h3>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={chartData} barSize={days <= 7 ? 28 : days <= 15 ? 16 : 10}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" vertical={false} />
                  <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#9ca3af' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#9ca3af' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    formatter={(value: number) => [value, t('reports.orderCount')]}
                    contentStyle={{ borderRadius: 8, border: '1px solid #e5e7eb', fontSize: 12 }}
                  />
                  <Bar dataKey="orders" fill={BRAND_LIGHT} radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* Per-period breakdown table */}
          {trend.dataPoints.length > 0 && (
            <div className="card overflow-hidden mt-6">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-100">
                    <tr>
                      <th className="text-start px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.trendDate')}</th>
                      <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.gross')}</th>
                      <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.refundTotal')}</th>
                      <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.trendDiscount')}</th>
                      <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.netRevenue')}</th>
                      <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.trendTax')}</th>
                      <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.orderCount')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {trend.dataPoints.map(p => (
                      <tr key={p.date} className="hover:bg-gray-50">
                        <td className="px-3 py-2.5 text-gray-700">{p.date}</td>
                        <td className="px-3 py-2.5 text-end tabular-nums text-gray-900">{fmtMoney(p.grossRevenueMinorUnit)}</td>
                        <td className="px-3 py-2.5 text-end tabular-nums text-orange-500">{fmtMoney(p.refundMinorUnit)}</td>
                        <td className="px-3 py-2.5 text-end tabular-nums text-red-500">{fmtMoney(p.discountMinorUnit)}</td>
                        <td className="px-3 py-2.5 text-end tabular-nums font-semibold text-gray-900">{fmtMoney(p.netRevenueMinorUnit)}</td>
                        <td className="px-3 py-2.5 text-end tabular-nums text-gray-600">{fmtMoney(p.taxMinorUnit)}</td>
                        <td className="px-3 py-2.5 text-end tabular-nums text-gray-600">{p.orderCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </>
  )
}

// ── Peak Hours Tab ───────────────────────────────────────────────────────────
interface HourData {
  day: number   // 0=Mon..6=Sun
  hour: number  // 0..23
  orders: number
}

function PeakHoursTab() {
  const { t } = useTranslation()
  const [peakData, setPeakData] = useState<HourData[]>([])
  const [loading, setLoading] = useState(false)
  const [selectedCell, setSelectedCell] = useState<{ day: number; hour: number; orders: number } | null>(null)

  useEffect(() => {
    const to = new Date()
    const from = new Date()
    from.setDate(to.getDate() - 30)
    const fromStr = from.toISOString().slice(0, 10)
    const toStr = to.toISOString().slice(0, 10)

    setLoading(true)
    reportApi.peak({ fromDate: fromStr, toDate: toStr })
      .then(peak => {
        const cells = Array.isArray(peak?.cells) ? peak.cells : []
        const grid: HourData[] = cells.map(cell => ({
          day: cell.dayOfWeek - 1,
          hour: cell.hour,
          orders: cell.orderCount,
        }))
        setPeakData(grid)
      })
      .catch(() => setPeakData([]))
      .finally(() => setLoading(false))
  }, [])

  const maxOrders = useMemo(() => Math.max(1, ...peakData.map(d => d.orders)), [peakData])

  const dayLabels = useMemo(() => [
    t('reports.peakDayMon'),
    t('reports.peakDayTue'),
    t('reports.peakDayWed'),
    t('reports.peakDayThu'),
    t('reports.peakDayFri'),
    t('reports.peakDaySat'),
    t('reports.peakDaySun'),
  ], [t])

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16 text-gray-400">
        <svg className="w-5 h-5 animate-spin mr-2" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        <span className="text-sm">Loading…</span>
      </div>
    )
  }

  if (peakData.length === 0) {
    return (
      <div className="text-center py-16 text-gray-400">
        <p className="text-sm">{t('reports.peakNoData')}</p>
      </div>
    )
  }

  return (
    <>
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-gray-700">{t('reports.peakTitle')}</h3>
        <p className="text-xs text-gray-400 mt-0.5">{t('reports.peakSubtitle')}</p>
      </div>

      <div className="card p-5 overflow-x-auto">
        <div className="inline-block min-w-[800px]">
          {/* Header row: hours */}
          <div className="flex">
            <div className="w-16 flex-shrink-0" /> {/* day label space */}
            {Array.from({ length: 24 }, (_, h) => (
              <div key={h} className="flex-1 text-center text-[10px] text-gray-400 pb-1">
                {h}
              </div>
            ))}
          </div>

          {/* Rows: days × hours */}
          {dayLabels.map((dayLabel, dayIdx) => (
            <div key={dayIdx} className="flex items-center">
              <div className="w-16 flex-shrink-0 text-xs text-gray-500 font-medium pr-2 text-right">
                {dayLabel}
              </div>
              {Array.from({ length: 24 }, (_, h) => {
                const cell = peakData.find(c => c.day === dayIdx && c.hour === h)
                const orders = cell?.orders ?? 0
                const intensity = orders / maxOrders
                const bgColor = getHeatColor(intensity)
                return (
                  <div
                    key={h}
                    className="flex-1 aspect-square m-[1px] rounded-sm cursor-pointer transition-transform hover:scale-125 hover:z-10 flex items-center justify-center"
                    style={{ backgroundColor: bgColor }}
                    title={`${dayLabel} ${h}:00 — ${orders} ${t('reports.peakOrders')}`}
                    onClick={() => setSelectedCell({ day: dayIdx, hour: h, orders })}
                  />
                )
              })}
            </div>
          ))}

          {/* Legend */}
          <div className="flex items-center gap-2 mt-4 text-xs text-gray-400">
            <span>0</span>
            <div className="flex h-3 flex-1 rounded-sm overflow-hidden">
              {[0, 0.2, 0.4, 0.6, 0.8, 1].map((v, i) => (
                <div key={i} className="flex-1" style={{ backgroundColor: getHeatColor(v) }} />
              ))}
            </div>
            <span>{maxOrders}+ {t('reports.peakOrders')}</span>
          </div>
        </div>
      </div>

      {/* Selected cell detail */}
      {selectedCell && (
        <div className="card p-4 mt-4 flex items-center gap-6">
          <div>
            <p className="text-xs text-gray-500">{dayLabels[selectedCell.day]}</p>
            <p className="text-lg font-semibold text-gray-900">{selectedCell.hour}:00</p>
          </div>
          <div>
            <p className="text-xs text-gray-500">{t('reports.orderCount')}</p>
            <p className="text-lg font-semibold text-brand-600">{selectedCell.orders}</p>
          </div>
          <button
            className="ml-auto text-xs text-gray-400 hover:text-gray-600"
            onClick={() => setSelectedCell(null)}
          >
            ✕
          </button>
        </div>
      )}
    </>
  )
}

// ── Heat color: white → light orange → SUNMI orange → dark orange ──────────
function getHeatColor(intensity: number): string {
  if (intensity <= 0) return '#f9fafb'
  const clamped = Math.min(1, Math.max(0, intensity))
  // Gradient: #FFF5EB → #FF5C00
  const r = Math.round(255 - clamped * 0)
  const g = Math.round(245 - clamped * 153)
  const b = Math.round(235 - clamped * 235)
  return `rgb(${r}, ${g}, ${b})`
}

// ── Shared StatCard ────────────────────────────────────────────────────────
function StatCard({ label, value, accent, small }: { label: string; value: string; accent?: boolean; small?: boolean }) {
  return (
    <div className={`stat-card ${accent ? 'ring-1 ring-brand-200' : ''}`}>
      <p className={`font-medium text-gray-500 uppercase tracking-wide ${small ? 'text-xs' : 'text-xs'}`}>{label}</p>
      <p className={`font-semibold tabular-nums ${accent ? 'text-brand-600 text-2xl' : small ? 'text-lg text-gray-700' : 'text-2xl text-gray-900'}`}>
        {value}
      </p>
    </div>
  )
}

// ── Products Tab ───────────────────────────────────────────────────────────────
function ProductsTab() {
  const { t } = useTranslation()
  const [preset, setPreset] = useState<Preset>('today')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [categories, setCategories] = useState<CategorySales[]>([])
  const [items, setItems] = useState<ItemSales[]>([])
  const [filterCat, setFilterCat] = useState('')
  const [sortBy, setSortBy] = useState<'revenue' | 'qty'>('revenue')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [from, to] = useMemo(() => rangeFor(preset, customFrom, customTo), [preset, customFrom, customTo])

  useEffect(() => {
    setLoading(true)
    setError(null)
    Promise.all([
      reportApi.categoryReport(from, to),
      reportApi.itemReport(from, to),
    ])
      .then(([cats, itms]) => { setCategories(Array.isArray(cats) ? cats : []); setItems(Array.isArray(itms) ? itms : []) })
      .catch(e => setError(e instanceof Error ? e.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [from, to])

  const filteredItems = useMemo(() => {
    const list = filterCat ? items.filter(i => i.categoryId === filterCat) : items
    return [...list].sort((a, b) =>
      sortBy === 'revenue' ? b.revenueMinorUnit - a.revenueMinorUnit : b.quantity - a.quantity
    )
  }, [items, filterCat, sortBy])

  const totalRevenue = categories.reduce((s, c) => s + c.revenueMinorUnit, 0)

  const PIE_COLORS = ['#FF5C00','#FF8533','#FFB380','#FFD6B8','#FFEDE0','#E0E0E0','#BDBDBD','#9E9E9E']

  return (
    <div className="space-y-6">
      {/* Date range */}
      <div className="card p-4 flex flex-wrap items-center gap-3">
        {(['today','yesterday','week','month'] as Preset[]).map(p => (
          <button key={p} onClick={() => setPreset(p)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === p ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
            {t(`reports.${p === 'today' ? 'today' : p === 'yesterday' ? 'yesterday' : p === 'week' ? 'last7' : 'thisMonth'}`)}
          </button>
        ))}
        <button onClick={() => setPreset('custom')}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === 'custom' ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
          {t('reports.custom')}
        </button>
        {preset === 'custom' && (
          <>
            <DatePicker value={customFrom} onChange={setCustomFrom} />
            <span className="text-gray-400 text-xs">—</span>
            <DatePicker value={customTo} onChange={setCustomTo} />
          </>
        )}
      </div>

      {error && <div className="p-3 rounded-lg bg-red-50 border border-red-100 text-sm text-red-700">{error}</div>}
      {loading && <p className="text-sm text-gray-400">加载中…</p>}

      {!loading && (
        <>
          {/* Category breakdown */}
          <div className="card p-6">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">{t('reports.catColName')}</h3>
            {categories.length === 0 ? (
              <p className="text-sm text-gray-400 py-4 text-center">暂无数据</p>
            ) : (
              <div className="flex gap-8 flex-wrap">
                {/* Simple SVG donut */}
                <svg viewBox="0 0 120 120" className="w-32 h-32 flex-shrink-0">
                  {(() => {
                    let cumAngle = -90
                    return categories.map((cat, i) => {
                      const share = totalRevenue > 0 ? cat.revenueMinorUnit / totalRevenue : 0
                      const angle = share * 360
                      const r = 50, cx = 60, cy = 60
                      const startRad = (cumAngle * Math.PI) / 180
                      const endRad = ((cumAngle + angle) * Math.PI) / 180
                      const x1 = cx + r * Math.cos(startRad), y1 = cy + r * Math.sin(startRad)
                      const x2 = cx + r * Math.cos(endRad), y2 = cy + r * Math.sin(endRad)
                      const large = angle > 180 ? 1 : 0
                      const d = `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`
                      cumAngle += angle
                      return <path key={cat.categoryId} d={d} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                    })
                  })()}
                  <circle cx="60" cy="60" r="30" fill="white" />
                </svg>
                {/* Category table */}
                <div className="flex-1 min-w-0 overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-100">
                        <th className="text-start py-2 text-xs text-gray-500 font-medium w-4"></th>
                        <th className="text-start py-2 text-xs text-gray-500 font-medium">{t('reports.catColName')}</th>
                        <th className="text-end py-2 text-xs text-gray-500 font-medium">{t('reports.catColQty')}</th>
                        <th className="text-end py-2 text-xs text-gray-500 font-medium">{t('reports.catColRevenue')}</th>
                        <th className="text-end py-2 text-xs text-gray-500 font-medium">{t('reports.catColShare')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {categories.map((cat, i) => (
                        <tr key={cat.categoryId} className="border-b border-gray-50">
                          <td className="py-2 pr-2">
                            <span className="inline-block w-3 h-3 rounded-sm" style={{ background: PIE_COLORS[i % PIE_COLORS.length] }} />
                          </td>
                          <td className="py-2 text-gray-800">{cat.categoryName}</td>
                          <td className="py-2 text-end tabular-nums text-gray-600">{cat.quantity}</td>
                          <td className="py-2 text-end tabular-nums text-gray-900 font-medium">{fmtMoney(cat.revenueMinorUnit)}</td>
                          <td className="py-2 text-end tabular-nums text-gray-500">{(cat.sharePermille / 10).toFixed(1)}%</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>

          {/* Item breakdown */}
          <div className="card p-6">
            <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
              <h3 className="text-sm font-semibold text-gray-700">{t('reports.itemColName')}</h3>
              <div className="flex items-center gap-3">
                <select className="input text-xs py-1 px-2 h-8"
                  value={filterCat} onChange={e => setFilterCat(e.target.value)}>
                  <option value="">{t('reports.itemFilterAll')}</option>
                  {categories.map(c => (
                    <option key={c.categoryId} value={c.categoryId}>{c.categoryName}</option>
                  ))}
                </select>
                <div className="flex rounded-lg overflow-hidden border border-gray-200">
                  <button onClick={() => setSortBy('revenue')}
                    className={`px-3 py-1 text-xs ${sortBy === 'revenue' ? 'bg-brand-500 text-white' : 'text-gray-600 hover:bg-gray-50'}`}>
                    {t('reports.itemColRevenue')}
                  </button>
                  <button onClick={() => setSortBy('qty')}
                    className={`px-3 py-1 text-xs ${sortBy === 'qty' ? 'bg-brand-500 text-white' : 'text-gray-600 hover:bg-gray-50'}`}>
                    {t('reports.itemColQty')}
                  </button>
                </div>
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-100">
                  <tr>
                    <th className="text-start px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.itemColName')}</th>
                    <th className="text-start px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.itemColCat')}</th>
                    <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.itemColQty')}</th>
                    <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.itemColOrders')}</th>
                    <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.itemColRevenue')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {filteredItems.map(item => (
                    <tr key={item.menuItemId} className="hover:bg-gray-50">
                      <td className="px-3 py-2.5 text-gray-900">{parseName(item.names)}</td>
                      <td className="px-3 py-2.5 text-gray-500 text-xs">{item.categoryName}</td>
                      <td className="px-3 py-2.5 text-end tabular-nums text-gray-700">{item.quantity}</td>
                      <td className="px-3 py-2.5 text-end tabular-nums text-gray-500">{item.orderCount}</td>
                      <td className="px-3 py-2.5 text-end tabular-nums font-medium text-gray-900">{fmtMoney(item.revenueMinorUnit)}</td>
                    </tr>
                  ))}
                  {filteredItems.length === 0 && (
                    <tr><td colSpan={5} className="px-3 py-8 text-center text-sm text-gray-400">暂无数据</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

// ── Staff Tab ───────────────────────────────────────────────────────────────
function StaffTab() {
  const { t } = useTranslation()
  const [preset, setPreset] = useState<Preset>('today')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [staff, setStaff] = useState<StaffReport[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [from, to] = useMemo(() => rangeFor(preset, customFrom, customTo), [preset, customFrom, customTo])

  useEffect(() => {
    setLoading(true)
    setError(null)
    reportApi.staffReport(from, to)
      .then(d => setStaff(Array.isArray(d) ? d : []))
      .catch(e => setError(e instanceof Error ? e.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [from, to])

  const safeStaff = Array.isArray(staff) ? staff : []
  const maxRevenue = safeStaff.reduce((m, s) => Math.max(m, s.revenueMinorUnit), 0)

  return (
    <div className="space-y-6">
      <div className="card p-4 flex flex-wrap items-center gap-3">
        {(['today','yesterday','week','month'] as Preset[]).map(p => (
          <button key={p} onClick={() => setPreset(p)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === p ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
            {t(`reports.${p === 'today' ? 'today' : p === 'yesterday' ? 'yesterday' : p === 'week' ? 'last7' : 'thisMonth'}`)}
          </button>
        ))}
        <button onClick={() => setPreset('custom')}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === 'custom' ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
          {t('reports.custom')}
        </button>
        {preset === 'custom' && (
          <>
            <DatePicker value={customFrom} onChange={setCustomFrom} />
            <span className="text-gray-400 text-xs">—</span>
            <DatePicker value={customTo} onChange={setCustomTo} />
          </>
        )}
      </div>

      {error && <div className="p-3 rounded-lg bg-red-50 border border-red-100 text-sm text-red-700">{error}</div>}

      {/* Summary cards */}
      {!loading && safeStaff.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <StatCard label={t('reports.staffColName')} value={`${safeStaff.length} 人`} />
          <StatCard label={t('reports.staffColRevenue')} value={fmtMoney(safeStaff.reduce((s, x) => s + x.revenueMinorUnit, 0))} accent />
          <StatCard label={t('reports.staffColOrders')} value={String(safeStaff.reduce((s, x) => s + x.orderCount, 0))} />
          <StatCard label={t('reports.staffColAvg')}
            value={fmtMoney(safeStaff.reduce((s,x)=>s+x.revenueMinorUnit,0) / Math.max(1, safeStaff.reduce((s,x)=>s+x.orderCount,0)))} />
        </div>
      )}

      <div className="card p-6">
        {loading ? (
          <p className="text-sm text-gray-400">加载中…</p>
        ) : safeStaff.length === 0 ? (
          <p className="text-sm text-gray-400 py-8 text-center">{t('reports.staffNoData')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  <th className="text-start px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.staffColName')}</th>
                  <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.staffColOrders')}</th>
                  <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.staffColRevenue')}</th>
                  <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.staffColAvg')}</th>
                  <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.staffColTip')}</th>
                  <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.staffColDiscount')}</th>
                  <th className="text-end px-3 py-2.5 text-xs text-gray-500 font-medium">{t('reports.refundTotal')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {safeStaff.map(s => (
                  <tr key={s.operatorId} className={`hover:bg-gray-50 ${s.revenueMinorUnit === maxRevenue ? 'bg-brand-50' : ''}`}>
                    <td className="px-3 py-2.5 font-medium text-gray-900">{s.operatorName}</td>
                    <td className="px-3 py-2.5 text-end tabular-nums text-gray-700">{s.orderCount}</td>
                    <td className="px-3 py-2.5 text-end tabular-nums font-semibold text-gray-900">{fmtMoney(s.revenueMinorUnit)}</td>
                    <td className="px-3 py-2.5 text-end tabular-nums text-gray-600">{fmtMoney(s.avgOrderValueMinorUnit)}</td>
                    <td className="px-3 py-2.5 text-end tabular-nums text-gray-600">{fmtMoney(s.tipMinorUnit)}</td>
                    <td className="px-3 py-2.5 text-end tabular-nums text-red-500">{fmtMoney(s.discountMinorUnit)}</td>
                    <td className="px-3 py-2.5 text-end tabular-nums text-orange-500">{fmtMoney(s.refundMinorUnit)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Channels & Hours Tab ────────────────────────────────────────────────────
function ChannelsTab() {
  const { t } = useTranslation()
  const [preset, setPreset] = useState<Preset>('today')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [orderTypes, setOrderTypes] = useState<OrderTypeSales[]>([])
  const [hourly, setHourly] = useState<HourlySales[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [from, to] = useMemo(() => rangeFor(preset, customFrom, customTo), [preset, customFrom, customTo])

  useEffect(() => {
    setLoading(true)
    setError(null)
    Promise.all([
      reportApi.orderTypeReport(from, to),
      reportApi.hourlyReport(from, to),
    ])
      .then(([ot, hr]) => { setOrderTypes(Array.isArray(ot) ? ot : []); setHourly(Array.isArray(hr) ? hr : []) })
      .catch(e => setError(e instanceof Error ? e.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [from, to])

  const safeOrderTypes = Array.isArray(orderTypes) ? orderTypes : []
  const safeHourly = Array.isArray(hourly) ? hourly : []

  const ORDER_TYPE_LABELS: Record<string, string> = {
    DINE_IN: t('reports.orderTypeDineIn'),
    TAKEAWAY: t('reports.orderTypeTakeaway'),
    DELIVERY: t('reports.orderTypeDelivery'),
    ONLINE: t('reports.orderTypeOnline'),
  }

  const TYPE_COLORS = ['#FF5C00','#FF8533','#FFB380','#BDBDBD']
  const maxRevenue = safeHourly.reduce((m, h) => Math.max(m, h.revenueMinorUnit), 0)

  return (
    <div className="space-y-6">
      <div className="card p-4 flex flex-wrap items-center gap-3">
        {(['today','yesterday','week','month'] as Preset[]).map(p => (
          <button key={p} onClick={() => setPreset(p)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === p ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
            {t(`reports.${p === 'today' ? 'today' : p === 'yesterday' ? 'yesterday' : p === 'week' ? 'last7' : 'thisMonth'}`)}
          </button>
        ))}
        <button onClick={() => setPreset('custom')}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === 'custom' ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
          {t('reports.custom')}
        </button>
        {preset === 'custom' && (
          <>
            <DatePicker value={customFrom} onChange={setCustomFrom} />
            <span className="text-gray-400 text-xs">—</span>
            <DatePicker value={customTo} onChange={setCustomTo} />
          </>
        )}
      </div>

      {error && <div className="p-3 rounded-lg bg-red-50 border border-red-100 text-sm text-red-700">{error}</div>}

      {!loading && (
        <>
          {/* Order type distribution */}
          <div className="card p-6">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">{t('reports.orderTypeColType')}</h3>
            {safeOrderTypes.length === 0 ? (
              <p className="text-sm text-gray-400 py-4 text-center">暂无数据</p>
            ) : (
              <div className="space-y-3">
                {safeOrderTypes.map((ot, i) => (
                  <div key={ot.type}>
                    <div className="flex justify-between text-xs text-gray-600 mb-1">
                      <span className="font-medium">{ORDER_TYPE_LABELS[ot.type] ?? ot.type}</span>
                      <span className="tabular-nums">{fmtMoney(ot.revenueMinorUnit)} · {ot.orderCount} 单 · {(ot.sharePermille / 10).toFixed(1)}%</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-2">
                      <div className="h-2 rounded-full transition-all"
                        style={{ width: `${ot.sharePermille / 10}%`, background: TYPE_COLORS[i % TYPE_COLORS.length] }} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* 24h revenue chart */}
          <div className="card p-6">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">{t('reports.hourlyColHour')}</h3>
            {maxRevenue === 0 ? (
              <p className="text-sm text-gray-400 py-4 text-center">暂无数据</p>
            ) : (
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={safeHourly} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
                  <XAxis dataKey="hour" tick={{ fontSize: 10, fill: '#9ca3af' }} tickFormatter={h => `${h}h`} />
                  <YAxis hide />
                  <Tooltip
                    contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid #e5e7eb' }}
                    formatter={(value: number, name: string) =>
                      name === 'revenueMinorUnit' ? [fmtMoney(value), t('reports.hourlyColRevenue')] : [value, t('reports.hourlyColOrders')]
                    }
                    labelFormatter={h => `${h}:00`}
                  />
                  <Bar dataKey="revenueMinorUnit" fill={BRAND} radius={[3,3,0,0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </>
      )}
    </div>
  )
}

// ── Payments Tab ─────────────────────────────────────────────────────────────
function PaymentsTab() {
  const { t } = useTranslation()
  const [preset, setPreset] = useState<Preset>('today')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [data, setData] = useState<PaymentMethodReport[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [from, to] = useMemo(() => rangeFor(preset, customFrom, customTo), [preset, customFrom, customTo])

  useEffect(() => {
    setLoading(true); setError(null)
    reportApi.paymentMethodReport(from, to)
      .then(d => setData(Array.isArray(d) ? d : []))
      .catch(() => setError(t('reports.errorLoad')))
      .finally(() => setLoading(false))
  }, [from, to, t])

  const total = useMemo(() => {
    const rows = Array.isArray(data) ? data : []
    return {
      payCount: rows.reduce((s, r) => s + r.paymentCount, 0),
      payAmt:   rows.reduce((s, r) => s + r.paymentAmountMinorUnit, 0),
      refCount: rows.reduce((s, r) => s + r.refundCount, 0),
      refAmt:   rows.reduce((s, r) => s + r.refundAmountMinorUnit, 0),
      net:      rows.reduce((s, r) => s + r.netAmountMinorUnit, 0),
    }
  }, [data])

  return (
    <div className="space-y-6">
      <div className="card p-4 flex flex-wrap items-center gap-3">
        {(['today','yesterday','week','month'] as Preset[]).map(p => (
          <button key={p} onClick={() => setPreset(p)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === p ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
            {t(`reports.${p === 'today' ? 'today' : p === 'yesterday' ? 'yesterday' : p === 'week' ? 'last7' : 'thisMonth'}`)}
          </button>
        ))}
        <button onClick={() => setPreset('custom')}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === 'custom' ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
          {t('reports.custom')}
        </button>
        {preset === 'custom' && (
          <>
            <DatePicker value={customFrom} onChange={setCustomFrom} />
            <span className="text-gray-400 text-xs">—</span>
            <DatePicker value={customTo} onChange={setCustomTo} />
          </>
        )}
      </div>

      {error && <div className="p-3 rounded-lg bg-red-50 border border-red-100 text-sm text-red-700">{error}</div>}

      {loading && <div className="text-sm text-gray-400 py-8 text-center">…</div>}

      {!loading && (
        <div className="card overflow-hidden">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-gray-600">{t('reports.payColMethod')}</th>
                <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.payColPayCount')}</th>
                <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.payColPayAmt')}</th>
                <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.payColRefCount')}</th>
                <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.payColRefAmt')}</th>
                <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.payColNet')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {data.length === 0 ? (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">{t('reports.noOrders')}</td></tr>
              ) : (
                <>
                  {data.map(row => (
                    <tr key={row.method} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {t(`dashboard.paymentLabels.${row.method}`, { defaultValue: row.method })}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums text-gray-700">{row.paymentCount}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-gray-900">{fmtMoney(row.paymentAmountMinorUnit)}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-gray-700">{row.refundCount}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-red-600">{row.refundAmountMinorUnit > 0 ? `-${fmtMoney(row.refundAmountMinorUnit)}` : '—'}</td>
                      <td className="px-4 py-3 text-right tabular-nums font-semibold text-gray-900">{fmtMoney(row.netAmountMinorUnit)}</td>
                    </tr>
                  ))}
                  <tr className="bg-gray-50 font-semibold">
                    <td className="px-4 py-3 text-gray-900">{t('reports.total')}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-gray-700">{total.payCount}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-gray-900">{fmtMoney(total.payAmt)}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-gray-700">{total.refCount}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-red-600">{total.refAmt > 0 ? `-${fmtMoney(total.refAmt)}` : '—'}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-gray-900">{fmtMoney(total.net)}</td>
                  </tr>
                </>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

// ── Cashier Shift Tab (交班记录) ──────────────────────────────────────────────
function CashierShiftTab() {
  const { t } = useTranslation()
  const [shifts, setShifts] = useState<CashierShift[]>([])
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState<CashierShift | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    shiftApi.list().then(setShifts).finally(() => setLoading(false))
  }, [])

  function selectShift(s: CashierShift) {
    setSelected(s)
    setDetailLoading(true)
    shiftApi.get(s.id).then(setSelected).finally(() => setDetailLoading(false))
  }

  const fmtTs = (ms: number) => new Date(ms).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })

  return (
    <div className="flex gap-0">
      {/* Left: shift list */}
      <div className="flex-1 min-w-0">
        <div className="card overflow-hidden">
          {loading ? (
            <div className="p-8 text-center text-sm text-gray-400">…</div>
          ) : shifts.length === 0 ? (
            <div className="p-8 text-center text-sm text-gray-400">{t('reports.shiftNoData')}</div>
          ) : (
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">{t('reports.shiftColPOS')}</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">{t('reports.shiftColOpened')}</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">{t('reports.shiftColClosed')}</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.shiftColExpected')}</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.shiftColActual')}</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.shiftColDiff')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {shifts.map(s => {
                  const expected = s.expectedCashMinorUnit
                  const actual   = s.actualClosingCashMinorUnit
                  const diff     = actual != null ? actual - expected : null
                  return (
                    <tr key={s.id} onClick={() => selectShift(s)}
                      className={`cursor-pointer transition-colors hover:bg-brand-50 ${selected?.id === s.id ? 'bg-brand-50' : ''}`}>
                      <td className="px-4 py-3 font-medium text-gray-900">{s.terminalName || s.terminalId}</td>
                      <td className="px-4 py-3 text-gray-600">{fmtTs(s.openedAt)}</td>
                      <td className="px-4 py-3 text-gray-600">{s.closedAt ? fmtTs(s.closedAt) : <span className="text-green-600 text-xs">{t('reports.shiftOpen')}</span>}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-gray-700">{fmtMoney(expected)}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-gray-700">{actual != null ? fmtMoney(actual) : '—'}</td>
                      <td className={`px-4 py-3 text-right tabular-nums font-medium ${diff != null && diff < 0 ? 'text-red-600' : 'text-gray-700'}`}>
                        {diff != null ? (diff < 0 ? `-${fmtMoney(-diff)}` : fmtMoney(diff)) : '—'}
                        {diff != null && diff < 0 && <span className="ml-1">⚠️</span>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Right: detail drawer */}
      {selected && (
        <div className="w-80 shrink-0 border-l border-gray-200 bg-white ml-6 -mr-8 -mt-8 p-6 overflow-y-auto max-h-screen sticky top-0">
          {detailLoading ? (
            <div className="text-sm text-gray-400 py-8 text-center">…</div>
          ) : (
            <>
              <div className="flex justify-between items-start mb-4">
                <h3 className="font-semibold text-gray-900">{t('reports.shiftDetailTitle')}</h3>
                <button onClick={() => setSelected(null)} className="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
              </div>
              {/* Meta */}
              <div className="text-xs text-gray-500 space-y-1 mb-4 pb-4 border-b border-gray-100">
                <div className="flex justify-between"><span>{t('reports.shiftNum')}</span><span className="font-medium text-gray-700">{selected.shiftNumber}</span></div>
                {selected.storeName && <div className="flex justify-between"><span>{t('reports.shiftStore')}</span><span className="font-medium text-gray-700">{selected.storeName}</span></div>}
                <div className="flex justify-between"><span>POS</span><span className="font-medium text-gray-700">{selected.terminalName || selected.terminalId}</span></div>
                <div className="flex justify-between"><span>{t('reports.shiftOpenedBy')}</span><span className="font-medium text-gray-700">{selected.openedByName || selected.openedByOperatorId} {fmtTs(selected.openedAt)}</span></div>
                {selected.closedAt && <div className="flex justify-between"><span>{t('reports.shiftClosedBy')}</span><span className="font-medium text-gray-700">{selected.closedByName || selected.closedByOperatorId || '—'} {fmtTs(selected.closedAt)}</span></div>}
              </div>
              {/* Cash drawer */}
              <div className="mb-4">
                <h4 className="text-xs font-semibold text-brand-600 uppercase tracking-wider mb-2">{t('reports.shiftCashDrawer')}</h4>
                <div className="space-y-1 text-xs">
                  {[
                    [t('reports.shiftOpeningCash'), selected.openingCashMinorUnit],
                    [t('reports.shiftCashPaid'), selected.cashPaymentsMinorUnit],
                    [t('reports.shiftCashRefunded'), selected.cashRefundsMinorUnit],
                    [t('reports.shiftMoneyIn'), selected.moneyInMinorUnit],
                    [t('reports.shiftMoneyOut'), selected.moneyOutMinorUnit],
                  ].map(([label, val]) => (
                    <div key={label as string} className="flex justify-between text-gray-600">
                      <span>{label as string}</span><span className="tabular-nums">{fmtMoney(val as number)}</span>
                    </div>
                  ))}
                  <div className="flex justify-between text-gray-700 font-medium border-t border-gray-100 pt-1">
                    <span>{t('reports.shiftExpected')}</span><span className="tabular-nums">{fmtMoney(selected.expectedCashMinorUnit)}</span>
                  </div>
                  {selected.actualClosingCashMinorUnit != null && (
                    <div className="flex justify-between text-gray-700 font-medium">
                      <span>{t('reports.shiftActual')}</span><span className="tabular-nums">{fmtMoney(selected.actualClosingCashMinorUnit)}</span>
                    </div>
                  )}
                  {selected.actualClosingCashMinorUnit != null && (
                    <div className={`flex justify-between font-bold ${selected.differencMinorUnit < 0 ? 'text-red-600' : 'text-gray-900'}`}>
                      <span>{t('reports.shiftDiff')}</span>
                      <span className="tabular-nums">{selected.differencMinorUnit < 0 ? `-${fmtMoney(-selected.differencMinorUnit)}` : fmtMoney(selected.differencMinorUnit)}</span>
                    </div>
                  )}
                </div>
              </div>
              {/* Sales summary */}
              <div className="mb-4">
                <h4 className="text-xs font-semibold text-brand-600 uppercase tracking-wider mb-2">{t('reports.shiftSalesSummary')}</h4>
                <div className="space-y-1 text-xs">
                  {[
                    [t('reports.gross'), selected.grossSalesMinorUnit, false],
                    [t('reports.refundTotal'), selected.refundsMinorUnit, false],
                    [t('reports.discount'), selected.discountsMinorUnit, false],
                    [t('reports.netRevenue'), selected.netSalesMinorUnit, true],
                    [t('reports.tax'), selected.taxMinorUnit, false],
                  ].map(([label, val, bold]) => (
                    <div key={label as string} className={`flex justify-between ${bold ? 'font-bold text-gray-900 border-t border-gray-100 pt-1' : 'text-gray-600'}`}>
                      <span>{label as string}</span><span className="tabular-nums">{fmtMoney(val as number)}</span>
                    </div>
                  ))}
                </div>
              </div>
              {/* Cash movements */}
              {selected.movements.length > 0 && (
                <div>
                  <h4 className="text-xs font-semibold text-brand-600 uppercase tracking-wider mb-2">{t('reports.shiftMovements')}</h4>
                  <div className="space-y-1 text-xs">
                    {selected.movements.map(m => (
                      <div key={m.id} className="flex justify-between text-gray-600">
                        <span>{new Date(m.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })} {m.operatorName || m.operatorId} — {m.description || (m.type === 'IN' ? t('reports.shiftMoneyIn') : t('reports.shiftMoneyOut'))}</span>
                        <span className={`tabular-nums font-medium ${m.type === 'OUT' ? 'text-red-600' : 'text-green-600'}`}>
                          {m.type === 'OUT' ? '-' : '+'}{fmtMoney(m.amountMinorUnit)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}

// ── Tax Report Tab ────────────────────────────────────────────────────────────
function TaxTab() {
  const { t } = useTranslation()
  const [preset, setPreset] = useState<Preset>('today')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [data, setData] = useState<TaxReportSummary | null>(null)
  const [loading, setLoading] = useState(false)

  const [from, to] = useMemo(() => rangeFor(preset, customFrom, customTo), [preset, customFrom, customTo])

  useEffect(() => {
    setLoading(true)
    reportApi.taxReport(from, to)
      .then(d => {
        if (d && typeof d === 'object' && Array.isArray((d as any).lines)) setData(d)
        else setData(null)
      })
      .finally(() => setLoading(false))
  }, [from, to])

  return (
    <div className="space-y-6">
      <div className="card p-4 flex flex-wrap items-center gap-3">
        {(['today','yesterday','week','month'] as Preset[]).map(p => (
          <button key={p} onClick={() => setPreset(p)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === p ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
            {t(`reports.${p === 'today' ? 'today' : p === 'yesterday' ? 'yesterday' : p === 'week' ? 'last7' : 'thisMonth'}`)}
          </button>
        ))}
        <button onClick={() => setPreset('custom')}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === 'custom' ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
          {t('reports.custom')}
        </button>
        {preset === 'custom' && (
          <>
            <DatePicker value={customFrom} onChange={setCustomFrom} />
            <span className="text-gray-400 text-xs">—</span>
            <DatePicker value={customTo} onChange={setCustomTo} />
          </>
        )}
      </div>

      {loading && <div className="text-sm text-gray-400 py-8 text-center">…</div>}

      {!loading && data && Array.isArray((data as any).lines) && (
        <>
          {/* KPI row */}
          <div className="grid grid-cols-3 gap-4">
            {[
              [t('reports.taxKpiTaxable'), data.taxableSalesMinorUnit],
              [t('reports.taxKpiNonTaxable'), data.nonTaxableSalesMinorUnit],
              [t('reports.taxKpiTotal'), data.totalNetSalesMinorUnit],
            ].map(([label, val]) => (
              <div key={label as string} className="card p-4">
                <p className="text-xs text-gray-500 mb-1">{label as string}</p>
                <p className="text-xl font-semibold text-gray-900 tabular-nums">{fmtMoney(val as number)}</p>
              </div>
            ))}
          </div>

          {/* Per-rate table */}
          <div className="card overflow-hidden">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">{t('reports.taxColName')}</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.taxColRate')}</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.taxColTaxable')}</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.taxColAmount')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {data.lines.length === 0 ? (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">{t('reports.noOrders')}</td></tr>
                ) : (
                  <>
                    {data.lines.map(line => (
                      <tr key={line.taxRateId} className="hover:bg-gray-50">
                        <td className="px-4 py-3 font-medium text-gray-900">{line.taxRateName}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-gray-700">{(line.ratePermille / 10).toFixed(1)}%</td>
                        <td className="px-4 py-3 text-right tabular-nums text-gray-700">{fmtMoney(line.taxableSalesMinorUnit)}</td>
                        <td className="px-4 py-3 text-right tabular-nums font-semibold text-gray-900">{fmtMoney(line.taxAmountMinorUnit)}</td>
                      </tr>
                    ))}
                    <tr className="bg-gray-50 font-semibold">
                      <td className="px-4 py-3 text-gray-900" colSpan={3}>{t('reports.total')}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-gray-900">{fmtMoney(data.lines.reduce((s, l) => s + l.taxAmountMinorUnit, 0))}</td>
                    </tr>
                  </>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}

// ── Modifiers Report Tab ──────────────────────────────────────────────────────
function ModifiersTab() {
  const { t } = useTranslation()
  const [preset, setPreset] = useState<Preset>('today')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [data, setData] = useState<ModifierSales[]>([])
  const [loading, setLoading] = useState(false)

  const [from, to] = useMemo(() => rangeFor(preset, customFrom, customTo), [preset, customFrom, customTo])

  useEffect(() => {
    setLoading(true)
    reportApi.modifierReport(from, to).then(d => setData(Array.isArray(d) ? d : [])).finally(() => setLoading(false))
  }, [from, to])

  return (
    <div className="space-y-6">
      <div className="card p-4 flex flex-wrap items-center gap-3">
        {(['today','yesterday','week','month'] as Preset[]).map(p => (
          <button key={p} onClick={() => setPreset(p)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === p ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
            {t(`reports.${p === 'today' ? 'today' : p === 'yesterday' ? 'yesterday' : p === 'week' ? 'last7' : 'thisMonth'}`)}
          </button>
        ))}
        <button onClick={() => setPreset('custom')}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${preset === 'custom' ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
          {t('reports.custom')}
        </button>
        {preset === 'custom' && (
          <>
            <DatePicker value={customFrom} onChange={setCustomFrom} />
            <span className="text-gray-400 text-xs">—</span>
            <DatePicker value={customTo} onChange={setCustomTo} />
          </>
        )}
      </div>

      {loading && <div className="text-sm text-gray-400 py-8 text-center">…</div>}

      {!loading && (
        <div className="card overflow-hidden">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-gray-600">{t('reports.modColGroup')}</th>
                <th className="px-4 py-3 text-left font-medium text-gray-600">{t('reports.modColOption')}</th>
                <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.modColQty')}</th>
                <th className="px-4 py-3 text-right font-medium text-gray-600">{t('reports.modColRevenue')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {!Array.isArray(data) || data.length === 0 ? (
                <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">{t('reports.modNoData')}</td></tr>
              ) : data.map(row => (
                <tr key={row.optionId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-500 text-xs">{row.groupName}</td>
                  <td className="px-4 py-3 font-medium text-gray-900">{row.optionName}</td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-700">{row.quantitySold}</td>
                  <td className="px-4 py-3 text-right tabular-nums font-semibold text-gray-900">{fmtMoney(row.revenueMinorUnit)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

// ── Hours Summary Tab ────────────────────────────────────────────────────────
function HoursTab() {
  const { t } = useTranslation()
  const [from, setFrom] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() - 30); d.setHours(0,0,0,0); return d.getTime()
  })
  const [to, setTo] = useState(() => {
    const d = new Date(); d.setHours(23,59,59,999); return d.getTime()
  })
  const [data, setData] = useState<HoursReportResponse | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    timecardApi.report(from, to)
      .then(d => setData(Array.isArray(d?.summaries) ? d : null))
      .finally(() => setLoading(false))
  }, [from, to])

  function fmtMinutes(minutes: number) {
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    return `${h}h ${m.toString().padStart(2,'0')}m`
  }

  return (
    <div>
      <div className="flex flex-wrap gap-3 mb-6 items-end">
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('reports.rangeFrom')}</label>
          <input type="date" className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm"
            value={new Date(from).toISOString().slice(0,10)}
            onChange={e => setFrom(new Date(e.target.value).getTime())} />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('reports.rangeTo')}</label>
          <input type="date" className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm"
            value={new Date(to).toISOString().slice(0,10)}
            onChange={e => { const d = new Date(e.target.value); d.setHours(23,59,59,999); setTo(d.getTime()) }} />
        </div>
      </div>

      {loading && <p className="text-gray-400 text-sm">…</p>}

      {!loading && data && (
        <>
          {/* KPI cards */}
          {(() => {
            const summaries = data.summaries ?? []
            const totalShifts = summaries.reduce((s,r)=>s+r.shiftsCount,0)
            return (
              <>
                <div className="grid grid-cols-3 gap-4 mb-6">
                  <div className="bg-white rounded-2xl border border-gray-100 p-4">
                    <p className="text-xs text-gray-500">{t('users.tabTimecards')}</p>
                    <p className="text-2xl font-bold mt-1">{summaries.length}</p>
                  </div>
                  <div className="bg-white rounded-2xl border border-gray-100 p-4">
                    <p className="text-xs text-gray-500">{t('reports.hoursColTotal')}</p>
                    <p className="text-2xl font-bold mt-1">{fmtMinutes(data.totalMinutes ?? 0)}</p>
                  </div>
                  <div className="bg-white rounded-2xl border border-gray-100 p-4">
                    <p className="text-xs text-gray-500">{t('reports.hoursColShifts')}</p>
                    <p className="text-2xl font-bold mt-1">{totalShifts}</p>
                  </div>
                </div>
                {summaries.length === 0
                  ? <p className="text-gray-400 text-sm">{t('reports.hoursNoData')}</p>
                  : (
                    <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
                      <table className="w-full text-sm">
                        <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                          <tr>
                            <th className="px-4 py-3 text-left">{t('reports.hoursColEmployee')}</th>
                            <th className="px-4 py-3 text-right">{t('reports.hoursColShifts')}</th>
                            <th className="px-4 py-3 text-right">{t('reports.hoursColTotal')}</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50">
                          {summaries.map(s => (
                            <tr key={s.operatorId}>
                              <td className="px-4 py-3 font-medium">{s.operatorName}</td>
                              <td className="px-4 py-3 text-right">{s.shiftsCount}</td>
                              <td className="px-4 py-3 text-right">{fmtMinutes(s.totalMinutes)}</td>
                            </tr>
                          ))}
                          <tr className="bg-gray-50 font-semibold">
                            <td className="px-4 py-3">{t('reports.hoursTotal')}</td>
                            <td className="px-4 py-3 text-right">{totalShifts}</td>
                            <td className="px-4 py-3 text-right">{fmtMinutes(data.totalMinutes ?? 0)}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  )
                }
              </>
            )
          })()}
        </>
      )}
    </div>
  )
}
