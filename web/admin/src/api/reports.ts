import { apiClient } from './client'

export interface ShiftReport {
  fromMs: number
  toMs: number
  orderCount: number
  grossRevenueMinorUnit: number
  netRevenueMinorUnit: number
  totalDiscountMinorUnit: number
  totalTipMinorUnit: number
  totalServiceChargeMinorUnit: number
  totalTaxMinorUnit: number
  totalGuestCount: number
  averageOrderValueMinorUnit: number
  averageSpendPerGuestMinorUnit: number
  paymentMethodBreakdown: Record<string, number>
}

export interface TrendDataPoint {
  date: string
  grossRevenueMinorUnit: number
  netRevenueMinorUnit: number
  orderCount: number
  guestCount: number
  averageOrderValueMinorUnit: number
  averageSpendPerGuestMinorUnit: number
  refundMinorUnit: number
  discountMinorUnit: number
  taxMinorUnit: number
}

export interface TrendSummary {
  totalGrossRevenue: number
  totalNetRevenue: number
  totalOrderCount: number
  totalGuestCount: number
  avgOrderValue: number
  avgSpendPerGuest: number
  growthFromPrevious: number | null
}

export interface TrendReport {
  dataPoints: TrendDataPoint[]
  summary: TrendSummary
}

export interface OrderSummary {
  id: string
  type: string
  tableId?: string
  sourceTerminalId: string
  guestCount: number
  status: string
  subtotalMinorUnit: number
  taxTotalMinorUnit: number
  serviceChargeMinorUnit: number
  tipMinorUnit: number
  discountMinorUnit: number
  orderNotes: string
  createdAt: number
  updatedAt: number
  operatorId: string
  operatorName: string
  paymentMethod?: string
}

export interface OrderItem {
  id: string
  orderId: string
  menuItemId: string
  menuItemNameSnapshot: string
  quantity: number
  unitPriceMinorUnit: number
  course: number
  status: string
  notes: string
  allergenSnapshot: string
}

export interface Payment {
  id: string
  orderId: string
  amountMinorUnit: number
  method: string
  status: string
  operatorId: string
  refundedPaymentId?: string | null
  createdAt: number
}

export interface OrderDetail {
  order: OrderSummary
  items: OrderItem[]
  payments: Payment[]
}

export interface OrderListResponse {
  orders: OrderSummary[]
  total: number
  page: number
  pageSize: number
}

export interface PeakHourCell {
  dayOfWeek: number   // 1=Mon … 7=Sun
  hour: number        // 0-23
  orderCount: number
}

export interface PeakReport {
  cells: PeakHourCell[]
  maxOrderCount: number
  totalOrders: number
}

export interface TopItem {
  menuItemId: string
  names: string
  quantity: number
  revenueMinorUnit: number
}

export interface CategorySales {
  categoryId: string
  categoryName: string
  quantity: number
  revenueMinorUnit: number
  orderCount: number
  sharePermille: number
}

export interface ItemSales {
  menuItemId: string
  names: string
  categoryId: string
  categoryName: string
  quantity: number
  orderCount: number
  revenueMinorUnit: number
}

export interface OrderTypeSales {
  type: string
  orderCount: number
  revenueMinorUnit: number
  guestCount: number
  sharePermille: number
}

export interface StaffReport {
  operatorId: string
  operatorName: string
  orderCount: number
  revenueMinorUnit: number
  avgOrderValueMinorUnit: number
  tipMinorUnit: number
  discountMinorUnit: number
  refundMinorUnit: number
}

export interface HourlySales {
  hour: number
  orderCount: number
  revenueMinorUnit: number
  avgOrderValueMinorUnit: number
}

export interface TaxReportLine {
  taxRateId: string
  taxRateName: string
  ratePermille: number
  taxableSalesMinorUnit: number
  taxAmountMinorUnit: number
}

export interface TaxReportSummary {
  taxableSalesMinorUnit: number
  nonTaxableSalesMinorUnit: number
  totalNetSalesMinorUnit: number
  lines: TaxReportLine[]
}

export interface ModifierSales {
  optionId: string
  optionName: string
  groupId: string
  groupName: string
  quantitySold: number
  revenueMinorUnit: number
}

export interface CashMovement {
  id: string
  shiftId: string
  type: string
  amountMinorUnit: number
  description: string
  operatorId: string
  operatorName: string
  createdAt: number
}

export interface CashierShift {
  id: string
  shiftNumber: number
  terminalId: string
  terminalName: string
  storeName: string
  openedByOperatorId: string
  openedByName: string
  closedByOperatorId?: string
  closedByName: string
  openedAt: number
  closedAt?: number
  openingCashMinorUnit: number
  actualClosingCashMinorUnit?: number
  cashPaymentsMinorUnit: number
  cashRefundsMinorUnit: number
  moneyInMinorUnit: number
  moneyOutMinorUnit: number
  expectedCashMinorUnit: number
  differencMinorUnit: number
  grossSalesMinorUnit: number
  refundsMinorUnit: number
  discountsMinorUnit: number
  netSalesMinorUnit: number
  taxMinorUnit: number
  movements: CashMovement[]
}

export interface PaymentMethodReport {
  method: string
  paymentCount: number
  paymentAmountMinorUnit: number
  refundCount: number
  refundAmountMinorUnit: number
  netAmountMinorUnit: number
}

export const reportApi = {
  shift: (from: number, to: number) =>
    apiClient.get<ShiftReport>(`/admin/reports/shift?from=${from}&to=${to}`).then(r => r.data),

  trend: (params: { fromDate: string; toDate: string; granularity?: string }) =>
    apiClient.get<TrendReport>(`/admin/reports/trend?fromDate=${params.fromDate}&toDate=${params.toDate}&granularity=${params.granularity ?? 'day'}`).then(r => r.data),

  peak: (params: { fromDate: string; toDate: string }) =>
    apiClient.get<PeakReport>(`/admin/reports/peak?fromDate=${params.fromDate}&toDate=${params.toDate}`).then(r => r.data),

  topItems: (params: { from: number; to: number; limit?: number }) =>
    apiClient.get<TopItem[]>(`/admin/reports/top-items?from=${params.from}&to=${params.to}&limit=${params.limit ?? 5}`).then(r => r.data),

  categoryReport: (from: number, to: number) =>
    apiClient.get<CategorySales[]>(`/admin/reports/category?from=${from}&to=${to}`).then(r => r.data),

  itemReport: (from: number, to: number, categoryId?: string) => {
    const q = categoryId ? `&categoryId=${categoryId}` : ''
    return apiClient.get<ItemSales[]>(`/admin/reports/items?from=${from}&to=${to}${q}`).then(r => r.data)
  },

  orderTypeReport: (from: number, to: number) =>
    apiClient.get<OrderTypeSales[]>(`/admin/reports/order-type?from=${from}&to=${to}`).then(r => r.data),

  staffReport: (from: number, to: number) =>
    apiClient.get<StaffReport[]>(`/admin/reports/staff?from=${from}&to=${to}`).then(r => r.data),

  hourlyReport: (from: number, to: number) =>
    apiClient.get<HourlySales[]>(`/admin/reports/hourly?from=${from}&to=${to}`).then(r => r.data),

  paymentMethodReport: (from: number, to: number) =>
    apiClient.get<PaymentMethodReport[]>(`/admin/reports/payment-methods?from=${from}&to=${to}`).then(r => r.data),

  taxReport: (from: number, to: number) =>
    apiClient.get<TaxReportSummary>(`/admin/reports/tax?from=${from}&to=${to}`).then(r => r.data),

  modifierReport: (from: number, to: number) =>
    apiClient.get<ModifierSales[]>(`/admin/reports/modifiers?from=${from}&to=${to}`).then(r => r.data),
}

export const shiftApi = {
  list: (page = 0, pageSize = 20) =>
    apiClient.get<CashierShift[]>(`/admin/shifts?page=${page}&pageSize=${pageSize}`).then(r => r.data),

  get: (id: string) =>
    apiClient.get<CashierShift>(`/admin/shifts/${id}`).then(r => r.data),
}

export const orderApi = {
  list: (params: { from?: number; to?: number; status?: string; query?: string; operatorId?: string; paymentMethod?: string; page?: number; pageSize?: number }) => {
    const q = new URLSearchParams()
    if (params.from != null) q.set('from', String(params.from))
    if (params.to != null) q.set('to', String(params.to))
    if (params.status) q.set('status', params.status)
    if (params.query) q.set('query', params.query)
    if (params.operatorId) q.set('operatorId', params.operatorId)
    if (params.paymentMethod) q.set('paymentMethod', params.paymentMethod)
    if (params.page != null) q.set('page', String(params.page))
    if (params.pageSize != null) q.set('pageSize', String(params.pageSize))
    return apiClient.get<OrderListResponse>(`/admin/orders?${q}`).then(r => r.data)
  },

  get: (id: string) =>
    apiClient.get<OrderDetail>(`/admin/orders/${id}`).then(r => r.data),

  settle: (id: string) =>
    apiClient.post(`/admin/orders/${id}/settle`, {}).then(r => r.data),

  confirmQr: (id: string) =>
    apiClient.post(`/admin/orders/${id}/confirm-qr`, {}).then(r => r.data),

  rejectQr: (id: string) =>
    apiClient.post(`/admin/orders/${id}/reject-qr`, {}).then(r => r.data),

  refund: (paymentId: string, amountMinorUnit: number) =>
    apiClient.post<OrderDetail>(`/admin/orders/payments/${paymentId}/refund`, { amountMinorUnit }).then(r => r.data),
}

export interface CurrencyFmt {
  currencySymbol: string
  minorDigits: number
  decimalSeparator: string
  groupingSeparator: string
}

const DEFAULT_CURRENCY_FMT: CurrencyFmt = { currencySymbol: '¥', minorDigits: 2, decimalSeparator: '.', groupingSeparator: ',' }

function loadCurrencyFmt(): CurrencyFmt {
  try {
    const s = localStorage.getItem('pos_region_config')
    if (s) {
      // Stored shape is RegionConfig ({ currencyCode, currencySymbol, currencyMinorDigits, ... }),
      // not CurrencyFmt — map field names and fall back to defaults for anything missing.
      const raw = JSON.parse(s) as Record<string, unknown>
      const minorDigits = Number(raw.minorDigits ?? raw.currencyMinorDigits)
      return {
        currencySymbol: typeof raw.currencySymbol === 'string' ? raw.currencySymbol : DEFAULT_CURRENCY_FMT.currencySymbol,
        minorDigits: Number.isFinite(minorDigits) ? minorDigits : DEFAULT_CURRENCY_FMT.minorDigits,
        decimalSeparator: typeof raw.decimalSeparator === 'string' ? raw.decimalSeparator : DEFAULT_CURRENCY_FMT.decimalSeparator,
        groupingSeparator: typeof raw.groupingSeparator === 'string' ? raw.groupingSeparator : DEFAULT_CURRENCY_FMT.groupingSeparator,
      }
    }
  } catch { /* fall through */ }
  return DEFAULT_CURRENCY_FMT
}

export function fmtMoney(minor: number, fmt?: CurrencyFmt) {
  const f = fmt ?? loadCurrencyFmt()
  const safeMinor = Number.isFinite(minor) ? minor : 0
  const divisor = Math.pow(10, f.minorDigits)
  const abs = Math.abs(safeMinor)
  const sign = safeMinor < 0 ? '-' : ''
  const intPart = Math.floor(abs / divisor)
  const fracPart = abs % divisor
  // format integer part with grouping separator
  const intStr = intPart.toString().replace(/\B(?=(\d{3})+(?!\d))/g, f.groupingSeparator)
  const amount = f.minorDigits > 0
    ? `${intStr}${f.decimalSeparator}${String(fracPart).padStart(f.minorDigits, '0')}`
    : intStr
  return `${sign}${f.currencySymbol}${amount}`
}

export function fmtDateTime(ms: number) {
  return new Date(ms).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}
