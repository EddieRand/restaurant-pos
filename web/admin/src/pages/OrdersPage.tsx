import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { orderApi, fmtMoney, fmtDateTime, type OrderSummary, type OrderDetail, type Payment } from '../api/reports'
import { userApi, type PosUser, paymentMethodApi, type PaymentMethodConfig } from '../api/admin'
import DatePicker from '../components/DatePicker'
import { useVisiblePolling } from '../hooks/useVisiblePolling'

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-500',
  IN_PROGRESS: 'bg-blue-100 text-blue-700',
  PLACED: 'bg-indigo-100 text-indigo-700',
  PENDING_CONFIRMATION: 'bg-purple-100 text-purple-700',
  READY_TO_CHECKOUT: 'bg-amber-100 text-amber-700',
  CLOSED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-600',
  SETTLED: 'bg-green-100 text-green-700',
  VOIDED: 'bg-red-100 text-red-600',
  REJECTED: 'bg-red-100 text-red-600',
}

export default function OrdersPage() {
  const { t } = useTranslation()

  const STATUS_LABELS: Record<string, string> = {
    DRAFT: t('orders.statuses.DRAFT'),
    IN_PROGRESS: t('orders.statuses.IN_PROGRESS'),
    PLACED: t('orders.statuses.PLACED'),
    PENDING_CONFIRMATION: '待确认',
    READY_TO_CHECKOUT: t('orders.statuses.READY_TO_CHECKOUT'),
    CLOSED: t('orders.statuses.SETTLED'),
    SETTLED: t('orders.statuses.SETTLED'),
    CANCELLED: t('orders.statuses.VOIDED'),
    VOIDED: t('orders.statuses.VOIDED'),
    REJECTED: '已拒绝',
  }

  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodConfig[]>([])
  useEffect(() => { paymentMethodApi.list().then(d => setPaymentMethods(Array.isArray(d) ? d : [])).catch(() => {}) }, [])

  const DEFAULT_PAYMENT_LABELS: Record<string, string> = {
    CASH: t('dashboard.paymentLabels.CASH'),
    CARD: t('dashboard.paymentLabels.CARD'),
    WECHAT: t('dashboard.paymentLabels.WECHAT'),
    ALIPAY: t('dashboard.paymentLabels.ALIPAY'),
    DELIVERY: t('dashboard.paymentLabels.DELIVERY'),
    OTHER: t('dashboard.paymentLabels.OTHER'),
  }
  const PAYMENT_LABELS: Record<string, string> = { ...DEFAULT_PAYMENT_LABELS }
  for (const pm of paymentMethods) PAYMENT_LABELS[pm.code] = pm.displayName

  function exportOrdersCsv(orders: OrderSummary[]) {
    const header = [t('orders.colOrder'), t('orders.colTable'), t('orders.colStatus'), t('orders.colGuests'), t('orders.netTotal'), t('orders.colTime')]
    const rows = orders.map(o => [
      o.id,
      o.tableId ?? '—',
      STATUS_LABELS[o.status] ?? o.status,
      String(o.guestCount),
      fmtMoney(o.subtotalMinorUnit - o.discountMinorUnit + o.taxTotalMinorUnit + o.serviceChargeMinorUnit),
      fmtDateTime(o.createdAt),
    ])
    const csv = [header, ...rows].map(r => r.join(',')).join('\n')
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = `orders-${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url)
  }

  const [orders, setOrders] = useState<OrderSummary[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const pageSize = 20

  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [operatorFilter, setOperatorFilter] = useState('')
  const [paymentMethodFilter, setPaymentMethodFilter] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [loading, setLoading] = useState(false)
  const [employees, setEmployees] = useState<PosUser[]>([])

  const [detail, setDetail] = useState<OrderDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [settling, setSettling] = useState<string | null>(null)
  const [qrActioning, setQrActioning] = useState<string | null>(null)

  const load = useCallback(() => {
    setLoading(true)
    const from = dateFrom ? new Date(dateFrom).getTime() : undefined
    const to = dateTo ? (new Date(dateTo).setHours(23, 59, 59, 999)) : undefined
    orderApi.list({
      query: query || undefined, status: statusFilter || undefined,
      operatorId: operatorFilter || undefined, paymentMethod: paymentMethodFilter || undefined,
      from, to, page, pageSize,
    })
      .then(res => { setOrders(res.orders); setTotal(res.total) })
      .finally(() => setLoading(false))
  }, [query, statusFilter, operatorFilter, paymentMethodFilter, dateFrom, dateTo, page])

  useVisiblePolling(load)
  useEffect(() => { userApi.list().then(setEmployees).catch(() => {}) }, [])

  function openDetail(id: string) {
    setDetailLoading(true)
    orderApi.get(id).then(setDetail).finally(() => setDetailLoading(false))
  }

  async function handleSettle(id: string) {
    if (!confirm(t('orders.settleConfirm'))) return
    setSettling(id)
    try {
      await orderApi.settle(id)
      load()
    } finally {
      setSettling(null)
    }
  }

  const [refundTarget, setRefundTarget] = useState<Payment | null>(null)
  const [refunding, setRefunding] = useState(false)

  async function handleRefund(amountMinorUnit: number) {
    if (!refundTarget) return
    setRefunding(true)
    try {
      const updated = await orderApi.refund(refundTarget.id, amountMinorUnit)
      setDetail(updated)
      setRefundTarget(null)
      load()
    } finally {
      setRefunding(false)
    }
  }

  async function handleQrAction(id: string, action: 'confirm' | 'reject') {
    if (action === 'reject' && !confirm('确认拒绝这笔 QR 订单？')) return
    setQrActioning(`${action}:${id}`)
    try {
      if (action === 'confirm') await orderApi.confirmQr(id)
      else await orderApi.rejectQr(id)
      load()
      if (detail?.order.id === id) {
        await orderApi.get(id).then(setDetail)
      }
    } finally {
      setQrActioning(null)
    }
  }

  const totalPages = Math.ceil(total / pageSize)

  return (
    <div className="p-8 w-full">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">{t('orders.title')}</h1>
          <p className="mt-1 text-sm text-gray-500">{t('orders.page', { current: page + 1, total: totalPages, count: total })}</p>
        </div>
        <button className="btn-secondary" onClick={() => exportOrdersCsv(orders)} disabled={orders.length === 0}>
          <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z" clipRule="evenodd" />
          </svg>
          {t('orders.exportCsv')}
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-4">
        <input
          className="input w-48"
          placeholder={t('orders.searchPlaceholder')}
          value={query}
          onChange={e => { setQuery(e.target.value); setPage(0) }}
        />
        <select
          className="input w-36"
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value); setPage(0) }}
        >
          <option value="">{t('orders.allStatus')}</option>
          {Object.entries(STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select
          className="input w-32"
          value={operatorFilter}
          onChange={e => { setOperatorFilter(e.target.value); setPage(0) }}
        >
          <option value="">{t('orders.allEmployees')}</option>
          {employees.map(u => <option key={u.id} value={u.id}>{u.displayName}</option>)}
        </select>
        <select
          className="input w-36"
          value={paymentMethodFilter}
          onChange={e => { setPaymentMethodFilter(e.target.value); setPage(0) }}
        >
          <option value="">{t('orders.allPayments')}</option>
          {Object.entries(PAYMENT_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <DatePicker
          value={dateFrom}
          onChange={v => { setDateFrom(v); setPage(0) }}
          placeholder={t('reports.rangeFrom')}
        />
        <span className="flex items-center text-gray-400 text-sm">{t('reports.rangeTo')}</span>
        <DatePicker
          value={dateTo}
          onChange={v => { setDateTo(v); setPage(0) }}
          placeholder={t('reports.rangeTo')}
        />
        {(query || statusFilter || operatorFilter || paymentMethodFilter || dateFrom || dateTo) && (
          <button className="btn-secondary text-xs" onClick={() => { setQuery(''); setStatusFilter(''); setOperatorFilter(''); setPaymentMethodFilter(''); setDateFrom(''); setDateTo(''); setPage(0) }}>
            {t('orders.clearFilter')}
          </button>
        )}
      </div>

      {/* Table */}
      <div className="card overflow-hidden mb-4">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50/50">
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('orders.colOrder')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('orders.colTable')}</th>
              <th className="px-4 py-3 text-center font-medium text-gray-500">{t('orders.colStatus')}</th>
              <th className="px-4 py-3 text-center font-medium text-gray-500">{t('orders.colGuests')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('orders.colOperator')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('orders.colPayment')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('orders.colAmount')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('orders.colTime')}</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading && (
              <tr><td colSpan={9} className="px-4 py-8 text-center text-gray-400">{t('orders.loading')}…</td></tr>
            )}
            {!loading && orders.length === 0 && (
              <tr><td colSpan={9} className="px-4 py-8 text-center text-gray-400">{t('orders.noOrders')}</td></tr>
            )}
            {orders.map(order => {
              const net = order.subtotalMinorUnit - order.discountMinorUnit + order.taxTotalMinorUnit + order.serviceChargeMinorUnit + order.tipMinorUnit
              const isQrOrder = order.sourceTerminalId?.startsWith('qr:')
              return (
                <tr key={order.id} className="hover:bg-gray-50/50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-gray-600 truncate max-w-[140px]">
                    <div className="flex items-center gap-2">
                      <span>{order.id.slice(0, 8)}…</span>
                      {isQrOrder && <span className="badge bg-slate-100 text-slate-600 font-sans">QR</span>}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-gray-600">{order.tableId ?? '—'}</td>
                  <td className="px-4 py-3 text-center">
                    <span className={`badge ${STATUS_COLORS[order.status] ?? 'bg-gray-100 text-gray-500'}`}>
                      {STATUS_LABELS[order.status] ?? order.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center text-gray-600">{order.guestCount}</td>
                  <td className="px-4 py-3 text-gray-600">{order.operatorName || '—'}</td>
                  <td className="px-4 py-3 text-gray-600">{order.paymentMethod ? (PAYMENT_LABELS[order.paymentMethod] ?? order.paymentMethod) : '—'}</td>
                  <td className="px-4 py-3 text-right font-mono font-medium text-gray-900">{fmtMoney(net)}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">{fmtDateTime(order.createdAt)}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-3">
                      {order.status === 'PENDING_CONFIRMATION' && (
                        <>
                          <button
                            className="text-xs font-medium text-green-600 hover:text-green-800 transition-colors disabled:opacity-50"
                            disabled={qrActioning != null}
                            onClick={() => handleQrAction(order.id, 'confirm')}
                          >
                            {qrActioning === `confirm:${order.id}` ? '确认中' : '确认'}
                          </button>
                          <button
                            className="text-xs font-medium text-red-600 hover:text-red-800 transition-colors disabled:opacity-50"
                            disabled={qrActioning != null}
                            onClick={() => handleQrAction(order.id, 'reject')}
                          >
                            {qrActioning === `reject:${order.id}` ? '拒绝中' : '拒绝'}
                          </button>
                        </>
                      )}
                      {order.status === 'READY_TO_CHECKOUT' && (
                        <button
                          className="text-xs font-medium text-amber-600 hover:text-amber-800 transition-colors disabled:opacity-50"
                          disabled={settling === order.id}
                          onClick={() => handleSettle(order.id)}
                        >
                          {settling === order.id ? t('orders.settling') : t('orders.settle')}
                        </button>
                      )}
                      <button
                        className="text-xs text-gray-400 hover:text-brand-600 transition-colors"
                        onClick={() => openDetail(order.id)}
                      >
                        {t('orders.detail')}
                      </button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-gray-500">
            {t('orders.page', { current: page + 1, total: totalPages, count: total })}
          </span>
          <div className="flex gap-2">
            <button
              className="btn-secondary text-xs"
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
            >
              {t('orders.prev')}
            </button>
            <button
              className="btn-secondary text-xs"
              disabled={page >= totalPages - 1}
              onClick={() => setPage(p => p + 1)}
            >
              {t('orders.next')}
            </button>
          </div>
        </div>
      )}

      {/* Order detail drawer */}
      {(detail || detailLoading) && (
        <OrderDetailDrawer
          detail={detail}
          loading={detailLoading}
          onClose={() => setDetail(null)}
          statusLabels={STATUS_LABELS}
          paymentLabels={PAYMENT_LABELS}
          onQrAction={handleQrAction}
          qrActioning={qrActioning}
          onRefund={setRefundTarget}
        />
      )}

      {refundTarget && (
        <RefundModal
          payment={refundTarget}
          paymentLabels={PAYMENT_LABELS}
          loading={refunding}
          onConfirm={handleRefund}
          onCancel={() => setRefundTarget(null)}
        />
      )}
    </div>
  )
}

// ── Order Detail Drawer ───────────────────────────────────────────────────────

function OrderDetailDrawer({
  detail, loading, onClose, statusLabels, paymentLabels, onQrAction, qrActioning, onRefund,
}: {
  detail: OrderDetail | null
  loading: boolean
  onClose: () => void
  statusLabels: Record<string, string>
  paymentLabels: Record<string, string>
  onQrAction: (id: string, action: 'confirm' | 'reject') => void
  qrActioning: string | null
  onRefund: (payment: Payment) => void
}) {
  const { t } = useTranslation()
  const order = detail?.order

  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/20 backdrop-blur-sm" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl flex flex-col">
        {/* Drawer header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{t('orders.detail')}</h2>
          <button onClick={onClose} className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
          </button>
        </div>

        {/* Drawer body */}
        <div className="flex-1 overflow-y-auto px-6 py-4 space-y-5">
          {loading && (
            <div className="flex items-center justify-center py-16 text-gray-400">{t('orders.loading')}…</div>
          )}

          {order && (
            <>
              {/* Order meta */}
              <div className="space-y-2">
                <Row label={t('orders.colOrder')} value={<span className="font-mono text-xs">{order.id}</span>} />
                {order.sourceTerminalId?.startsWith('qr:') && (
                  <Row label="来源" value={<span className="badge bg-slate-100 text-slate-600">QR {order.sourceTerminalId.slice(3)}</span>} />
                )}
                <Row label={t('orders.colStatus')} value={
                  <span className={`badge ${STATUS_COLORS[order.status] ?? 'bg-gray-100 text-gray-500'}`}>
                    {statusLabels[order.status] ?? order.status}
                  </span>
                } />
                <Row label={t('orders.colTable')} value={order.tableId ?? '外卖/自取'} />
                <Row label={t('orders.colGuests')} value={String(order.guestCount)} />
                <Row label={t('orders.colTime')} value={fmtDateTime(order.createdAt)} />
                {order.orderNotes && <Row label={t('orders.detailNote')} value={order.orderNotes} />}
              </div>

              {order.status === 'PENDING_CONFIRMATION' && (
                <div className="grid grid-cols-2 gap-3">
                  <button
                    className="btn-primary justify-center disabled:opacity-50"
                    disabled={qrActioning != null}
                    onClick={() => onQrAction(order.id, 'confirm')}
                  >
                    {qrActioning === `confirm:${order.id}` ? '确认中' : '确认接单'}
                  </button>
                  <button
                    className="btn-secondary justify-center text-red-600 hover:text-red-700 disabled:opacity-50"
                    disabled={qrActioning != null}
                    onClick={() => onQrAction(order.id, 'reject')}
                  >
                    {qrActioning === `reject:${order.id}` ? '拒绝中' : '拒绝订单'}
                  </button>
                </div>
              )}

              <Divider />

              {/* Items */}
              <div>
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">{t('orders.detailItems')}</h3>
                <div className="space-y-2">
                  {detail!.items.map(item => {
                    let name = item.menuItemNameSnapshot
                    try { name = (JSON.parse(item.menuItemNameSnapshot) as Record<string, string>)['zh'] ?? name } catch { /* */ }
                    return (
                      <div key={item.id} className="flex items-center justify-between text-sm">
                        <div>
                          <span className="text-gray-900">{name}</span>
                          {item.notes && <span className="text-gray-400 text-xs ml-1">· {item.notes}</span>}
                        </div>
                        <div className="flex items-center gap-3 text-right">
                          <span className="text-gray-500">×{item.quantity}</span>
                          <span className="font-mono text-gray-900 w-20">
                            {fmtMoney(item.unitPriceMinorUnit * item.quantity)}
                          </span>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>

              <Divider />

              {/* Totals */}
              <div className="space-y-1.5 text-sm">
                <Row label={t('orders.subtotal')} value={fmtMoney(order.subtotalMinorUnit)} mono />
                {order.discountMinorUnit > 0 && <Row label={t('orders.discount')} value={`- ${fmtMoney(order.discountMinorUnit)}`} mono className="text-green-600" />}
                {order.taxTotalMinorUnit > 0 && <Row label={t('orders.tax')} value={fmtMoney(order.taxTotalMinorUnit)} mono />}
                {order.serviceChargeMinorUnit > 0 && <Row label={t('orders.serviceCharge')} value={fmtMoney(order.serviceChargeMinorUnit)} mono />}
                {order.tipMinorUnit > 0 && <Row label={t('orders.tip')} value={fmtMoney(order.tipMinorUnit)} mono />}
                <Divider />
                <div className="flex justify-between font-semibold">
                  <span className="text-gray-900">{t('orders.total')}</span>
                  <span className="font-mono text-brand-600">
                    {fmtMoney(
                      order.subtotalMinorUnit - order.discountMinorUnit +
                      order.taxTotalMinorUnit + order.serviceChargeMinorUnit + order.tipMinorUnit
                    )}
                  </span>
                </div>
              </div>

              {/* Payments */}
              {detail!.payments.length > 0 && (
                <>
                  <Divider />
                  <div>
                    <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">{t('orders.detailPayments')}</h3>
                    <div className="space-y-2">
                      {detail!.payments.map(p => {
                        const isRefund = !!p.refundedPaymentId
                        return (
                          <div key={p.id} className="flex items-center justify-between text-sm">
                            <span className="text-gray-600 flex items-center gap-2">
                              {paymentLabels[p.method] ?? p.method}
                              {p.status === 'REFUNDED' && !isRefund && (
                                <span className="badge bg-gray-100 text-gray-500">{t('orders.refunded')}</span>
                              )}
                            </span>
                            <div className="flex items-center gap-3">
                              <span className={`font-mono ${isRefund ? 'text-red-600' : 'text-gray-900'}`}>
                                {isRefund ? '-' : ''}{fmtMoney(p.amountMinorUnit)}
                              </span>
                              {p.status === 'PAID' && (() => {
                                const refunded = detail!.payments
                                  .filter(r => r.refundedPaymentId === p.id)
                                  .reduce((s, r) => s + r.amountMinorUnit, 0)
                                const remaining = p.amountMinorUnit - refunded
                                if (remaining <= 0) return null
                                return (
                                  <button
                                    className="text-xs font-medium text-brand-600 hover:text-brand-800 transition-colors"
                                    onClick={() => onRefund({ ...p, amountMinorUnit: remaining, id: p.id })}
                                  >
                                    {t('orders.refund')}
                                  </button>
                                )
                              })()}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </>
  )
}

function Row({ label, value, mono, className }: {
  label: string; value: React.ReactNode; mono?: boolean; className?: string
}) {
  return (
    <div className="flex items-center justify-between text-sm">
      <span className="text-gray-500">{label}</span>
      <span className={`${mono ? 'font-mono' : ''} text-gray-900 ${className ?? ''}`}>{value}</span>
    </div>
  )
}

function Divider() {
  return <hr className="border-gray-100" />
}

function RefundModal({ payment, paymentLabels, loading, onConfirm, onCancel }: {
  payment: Payment
  paymentLabels: Record<string, string>
  loading: boolean
  onConfirm: (amountMinorUnit: number) => void
  onCancel: () => void
}) {
  const { t } = useTranslation()
  const max = payment.amountMinorUnit
  const [amount, setAmount] = useState(String(max))
  const [error, setError] = useState('')

  function handleConfirm() {
    const value = Math.round(Number(amount) * 1)
    if (!Number.isFinite(value) || value <= 0 || value > max) {
      setError(t('orders.refundInvalidAmount', { max: fmtMoney(max) }))
      return
    }
    onConfirm(value)
  }

  return (
    <>
      <div className="fixed inset-0 z-[60] bg-black/30 flex items-center justify-center" onClick={onCancel}>
        <div className="bg-white rounded-xl shadow-xl w-full max-w-sm p-6 space-y-4" onClick={e => e.stopPropagation()}>
          <h3 className="text-base font-semibold text-gray-900">{t('orders.refundTitle')}</h3>
          <Row label={t('orders.refundOriginal')} value={`${paymentLabels[payment.method] ?? payment.method} · ${fmtMoney(payment.amountMinorUnit)}`} />
          <div>
            <label className="block text-sm text-gray-600 mb-1">{t('orders.refundAmount')}</label>
            <input
              type="number"
              className="input w-full"
              value={amount}
              min={1}
              max={max}
              onChange={e => { setAmount(e.target.value); setError('') }}
            />
            <p className="text-xs text-gray-400 mt-1">{t('orders.refundMax', { amount: fmtMoney(max) })}</p>
            {error && <p className="text-xs text-red-600 mt-1">{error}</p>}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button className="btn-secondary text-sm" onClick={onCancel} disabled={loading}>
              {t('orders.refundCancel')}
            </button>
            <button className="btn-primary text-sm" onClick={handleConfirm} disabled={loading}>
              {loading ? '...' : t('orders.refundConfirm')}
            </button>
          </div>
        </div>
      </div>
    </>
  )
}
