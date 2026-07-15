import { apiClient } from './client'

export interface PosUser {
  id: string
  displayName: string
  role: string
  roleDisplayName: string
  isActive: boolean
  createdAt: number
  hourlyWageMinorUnit: number
}

export interface Setting {
  key: string
  value: string
}

export interface PaymentMethodConfig {
  id: string
  code: string
  baseType: string
  displayName: string
  color: string
  sortOrder: number
  isActive: boolean
}

export const paymentMethodApi = {
  list: () => apiClient.get<PaymentMethodConfig[]>('/admin/payment-methods').then(r => r.data),

  create: (data: Omit<PaymentMethodConfig, 'id'>) =>
    apiClient.post<{ id: string }>('/admin/payment-methods', data).then(r => r.data),

  update: (id: string, patch: Partial<Omit<PaymentMethodConfig, 'id' | 'code' | 'baseType'>>) =>
    apiClient.patch(`/admin/payment-methods/${id}`, patch),

  delete: (id: string) => apiClient.delete(`/admin/payment-methods/${id}`),
}

export const userApi = {
  list: () => apiClient.get<PosUser[]>('/admin/users').then(r => r.data),

  create: (data: { id: string; displayName: string; role: string; pin: string; hourlyWageMinorUnit?: number }) =>
    apiClient.post('/admin/users', data),

  update: (id: string, patch: { displayName?: string; role?: string; pin?: string; isActive?: boolean; hourlyWageMinorUnit?: number }) =>
    apiClient.patch(`/admin/users/${id}`, patch),
}

export interface ShiftSchedule {
  id: string
  operatorId: string
  operatorName: string
  date: string
  startTime: string
  endTime: string
  notes: string
}

export interface LaborCostSummaryDto {
  operatorId: string
  operatorName: string
  hourlyWageMinorUnit: number
  scheduledMinutes: number
  actualMinutes: number
  scheduledCostMinorUnit: number
  actualCostMinorUnit: number
}

export interface LaborCostReportResponse {
  summaries: LaborCostSummaryDto[]
  totalScheduledCostMinorUnit: number
  totalActualCostMinorUnit: number
}

export const scheduleApi = {
  list: (params: { from?: string; to?: string; operatorId?: string } = {}) => {
    const qs = new URLSearchParams()
    if (params.from) qs.set('from', params.from)
    if (params.to) qs.set('to', params.to)
    if (params.operatorId) qs.set('operatorId', params.operatorId)
    const suffix = qs.toString() ? `?${qs.toString()}` : ''
    return apiClient.get<ShiftSchedule[]>(`/admin/schedules${suffix}`).then(r => r.data)
  },

  create: (data: { operatorId: string; date: string; startTime: string; endTime: string; notes?: string }) =>
    apiClient.post<{ id: string }>('/admin/schedules', data).then(r => r.data),

  update: (id: string, patch: { date?: string; startTime?: string; endTime?: string; notes?: string }) =>
    apiClient.patch(`/admin/schedules/${id}`, patch),

  delete: (id: string) => apiClient.delete(`/admin/schedules/${id}`),

  laborCost: (from: string, to: string) =>
    apiClient.get<LaborCostReportResponse>(`/admin/schedules/labor-cost?from=${from}&to=${to}`).then(r => r.data),
}

export interface Section {
  id: string
  name: string
  sortOrder: number
}

export interface TableConfig {
  id: string
  name: string
  sectionId: string
  capacity: number
  shape: 'square' | 'round' | 'rect'
  x: number
  y: number
  w: number
  h: number
}

export interface Ingredient {
  id: string
  name: string
  category: string
  unit: string             // base unit (used for stock, BOM, movements)
  purchaseUnit?: string    // purchase unit (e.g. "箱"); if absent, same as unit
  purchaseUnitFactor?: number // 1 purchaseUnit = factor * unit (e.g. 1箱 = 24罐)
  safetyStock: number
  currentStock: number
  createdAt: number
}

export type MovementType = 'IN' | 'OUT' | 'ADJUST'

export interface StockMovement {
  id: string
  ingredientId: string
  type: MovementType
  qty: number      // always positive; direction determined by type (OUT subtracts)
  note: string
  createdAt: number
}

export type PurchaseOrderStatus = 'DRAFT' | 'CONFIRMED'

export interface PurchaseOrderItem {
  id: string
  orderId: string
  ingredientId: string
  qty: number
  unitCost: number   // minor units (e.g. cents)
}

export interface Supplier {
  id: string
  name: string
  contact: string   // 联系人
  phone: string
  note: string
  createdAt: number
}

export const supplierApi = {
  list: () => apiClient.get<Supplier[]>('/admin/suppliers').then(r => r.data),
  create: (data: Omit<Supplier, 'id' | 'createdAt'>) => apiClient.post<Supplier>('/admin/suppliers', data).then(r => r.data),
  update: (id: string, patch: Partial<Omit<Supplier, 'id' | 'createdAt'>>) => apiClient.patch(`/admin/suppliers/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/suppliers/${id}`),
}

export interface PurchaseOrder {
  id: string
  status: PurchaseOrderStatus
  supplier: string
  supplierId?: string   // optional link to Supplier record
  note: string
  createdAt: number
  confirmedAt?: number
  items: PurchaseOrderItem[]
}

export const purchaseOrderApi = {
  list: () => apiClient.get<PurchaseOrder[]>('/admin/purchase-orders').then(r => r.data),
  create: (data: { supplier: string; supplierId?: string; note: string; items: Omit<PurchaseOrderItem, 'id' | 'orderId'>[] }) =>
    apiClient.post<PurchaseOrder>('/admin/purchase-orders', data).then(r => r.data),
  confirm: (id: string) =>
    apiClient.post<PurchaseOrder>(`/admin/purchase-orders/${id}/confirm`, {}).then(r => r.data),
  delete: (id: string) => apiClient.delete(`/admin/purchase-orders/${id}`),
}

// Inventory report
export interface IngredientMovementSummary {
  ingredientId: string
  name: string
  unit: string
  openingStock: number   // stock at start of period (approximated)
  totalIn: number
  totalOut: number
  totalAdjust: number
  closingStock: number   // current stock
  safetyStock: number
}

export interface PurchaseSummaryBySupplier {
  supplier: string
  orderCount: number
  totalMinorUnit: number
}

export interface InventoryReport {
  from: number
  to: number
  movements: IngredientMovementSummary[]
  purchaseBySupplier: PurchaseSummaryBySupplier[]
  lowStockCount: number
}

export const inventoryReportApi = {
  summary: (from: number, to: number) =>
    apiClient.get<InventoryReport>(`/admin/inventory/report?from=${from}&to=${to}`).then(r => r.data),
}

// BOM (Bill of Materials / 食谱)
export interface BomLine {
  id: string
  menuItemId: string
  ingredientId: string
  qty: number           // quantity consumed per serving of the menu item
  lastUnitCost?: number // minor units; from latest confirmed PO for this ingredient
}

export interface BomRecipe {
  menuItemId: string
  lines: BomLine[]
  estimatedCostMinorUnit?: number  // sum of qty * lastUnitCost across all lines
}

export const bomApi = {
  get: (menuItemId: string) =>
    apiClient.get<BomRecipe>(`/admin/bom/${menuItemId}`).then(r => r.data),
  save: (menuItemId: string, lines: Omit<BomLine, 'id' | 'menuItemId'>[]) =>
    apiClient.put<BomRecipe>(`/admin/bom/${menuItemId}`, { lines }).then(r => r.data),
}

export type OutboundType = 'ISSUE' | 'WASTE' | 'GIFT' | 'OTHER'
export type OutboundOrderStatus = 'DRAFT' | 'CONFIRMED'

export interface OutboundItem {
  id: string
  orderId: string
  ingredientId: string
  qty: number
}

export interface OutboundOrder {
  id: string
  status: OutboundOrderStatus
  type: OutboundType
  note: string
  createdAt: number
  confirmedAt?: number
  items: OutboundItem[]
}

export const outboundApi = {
  list: () => apiClient.get<OutboundOrder[]>('/admin/outbound-orders').then(r => r.data),
  create: (data: { type: OutboundType; note: string; items: { ingredientId: string; qty: number }[] }) =>
    apiClient.post<OutboundOrder>('/admin/outbound-orders', data).then(r => r.data),
  confirm: (id: string) => apiClient.post<OutboundOrder>(`/admin/outbound-orders/${id}/confirm`, {}).then(r => r.data),
  delete: (id: string) => apiClient.delete(`/admin/outbound-orders/${id}`),
}

export type StocktakeStatus = 'DRAFT' | 'SUBMITTED'

export interface StocktakeItem {
  id: string
  orderId: string
  ingredientId: string
  systemQty: number   // snapshot of currentStock at stocktake creation
  actualQty: number   // filled in by user
}

export interface StocktakeOrder {
  id: string
  status: StocktakeStatus
  note: string
  createdAt: number
  submittedAt?: number
  items: StocktakeItem[]
}

export const stocktakeApi = {
  list: () => apiClient.get<StocktakeOrder[]>('/admin/stocktakes').then(r => r.data),
  create: (note: string) => apiClient.post<StocktakeOrder>('/admin/stocktakes', { note }).then(r => r.data),
  submit: (id: string) => apiClient.post<StocktakeOrder>(`/admin/stocktakes/${id}/submit`, {}).then(r => r.data),
  updateItem: (orderId: string, itemId: string, actualQty: number) =>
    apiClient.patch(`/admin/stocktakes/${orderId}/items/${itemId}`, { actualQty }),
}

export const stockMovementApi = {
  list: (ingredientId: string) =>
    apiClient.get<StockMovement[]>(`/admin/stock-movements?ingredientId=${ingredientId}`).then(r => r.data),
  create: (data: Omit<StockMovement, 'id' | 'createdAt'>) =>
    apiClient.post<StockMovement>('/admin/stock-movements', data).then(r => r.data),
}

export const ingredientApi = {
  list: () => apiClient.get<Ingredient[]>('/admin/ingredients').then(r => r.data),
  create: (data: Omit<Ingredient, 'id' | 'createdAt'>) => apiClient.post<Ingredient>('/admin/ingredients', data).then(r => r.data),
  update: (id: string, patch: Partial<Omit<Ingredient, 'id' | 'createdAt'>>) => apiClient.patch(`/admin/ingredients/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/ingredients/${id}`),
}

export const sectionApi = {
  list: () => apiClient.get<Section[]>('/admin/sections').then(r => r.data),
  create: (data: Omit<Section, 'id'>) => apiClient.post('/admin/sections', data),
  update: (id: string, patch: Partial<Section>) => apiClient.patch(`/admin/sections/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/sections/${id}`),
}

export const tableApi = {
  list: () => apiClient.get<TableConfig[]>('/admin/tables').then(r => r.data),
  create: (data: Omit<TableConfig, 'id'>) => apiClient.post('/admin/tables', data),
  update: (id: string, patch: Partial<TableConfig>) => apiClient.patch(`/admin/tables/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/tables/${id}`),
}

export const settingsApi = {
  list: () => apiClient.get<Setting[]>('/admin/settings').then(r => r.data),

  get: (key: string) => apiClient.get<Setting>(`/admin/settings/${key}`).then(r => r.data),

  set: (key: string, value: string) =>
    apiClient.put(`/admin/settings/${key}`, { key, value }),

  getRegionConfig: async () => {
    try {
      const setting = await settingsApi.get('regionConfig')
      return normalizeRegionConfig(JSON.parse(setting.value))
    } catch {
      try {
        const legacy = await settingsApi.get('region-config')
        return normalizeRegionConfig(JSON.parse(legacy.value))
      } catch {
        return normalizeRegionConfig(DEFAULT_REGION_CONFIG)
      }
    }
  },

  setRegionConfig: (config: RegionConfig) => {
    const normalized = normalizeRegionConfig(config)
    return settingsApi.set('regionConfig', JSON.stringify(normalized))
  },
}

export interface QrOrderingAppearance {
  themeColor: string
  bannerImageUrl: string
  menuCardStyle: 'grid' | 'list'
}

export interface QrOrderingConfig {
  enabled: boolean
  supportedOrderTypes: string[]
  menuOnlyMode: boolean
  paymentTiming: 'MENU_ONLY' | 'PAY_BEFORE_SUBMIT' | 'PAY_AFTER_SUBMIT' | 'PAY_AT_END' | 'STAFF_COLLECTS'
  firePolicy: 'AUTO_FIRE' | 'STAFF_CONFIRM' | 'BY_ORDER_TYPE'
  customerIdentityPolicy: 'BY_ORDER_TYPE' | 'ANONYMOUS' | 'CONTACT_REQUIRED'
  appearance: QrOrderingAppearance
}

export interface QrCode {
  code: string
  scope: 'MENU' | 'TABLE' | 'PICKUP' | 'DELIVERY'
  tableId?: string | null
  enabled: boolean
  expiresAt?: number | null
  createdAt: number
  updatedAt: number
}

export interface TableQrBinding {
  tableId: string
  tableName: string
  sectionId: string
  sectionName: string
  capacity: number
  status: string
  currentQr?: QrCode | null
  activeCodeCount: number
  disabledCodeCount: number
  customerUrl?: string | null
}

export interface TableQrSection {
  id: string
  name: string
  tables: TableQrBinding[]
}

export interface TableQrBindingResponse {
  sections: TableQrSection[]
  unassignedCodes: QrCode[]
}

export const DEFAULT_QR_ORDERING_CONFIG: QrOrderingConfig = {
  enabled: true,
  supportedOrderTypes: ['DINE_IN', 'TAKEAWAY', 'DELIVERY'],
  menuOnlyMode: false,
  paymentTiming: 'PAY_AT_END',
  firePolicy: 'BY_ORDER_TYPE',
  customerIdentityPolicy: 'BY_ORDER_TYPE',
  appearance: {
    themeColor: '#FF5C00',
    bannerImageUrl: '',
    menuCardStyle: 'grid',
  },
}

export const qrOrderingApi = {
  getConfig: async () => {
    try {
      const data = await apiClient.get<Partial<QrOrderingConfig>>('/admin/qr-ordering/config').then(r => r.data)
      return {
        ...DEFAULT_QR_ORDERING_CONFIG,
        ...data,
        appearance: { ...DEFAULT_QR_ORDERING_CONFIG.appearance, ...data.appearance },
      }
    } catch {
      return DEFAULT_QR_ORDERING_CONFIG
    }
  },
  saveConfig: (config: QrOrderingConfig) =>
    apiClient.put<QrOrderingConfig>('/admin/qr-ordering/config', config).then(r => r.data),
  tableBindings: () =>
    apiClient.get<TableQrBindingResponse>('/admin/qr-ordering/table-bindings').then(r => r.data),
  generateTableQr: (tableId: string) =>
    apiClient.post<TableQrBinding>(`/admin/qr-ordering/tables/${encodeURIComponent(tableId)}/qr`, {}).then(r => r.data),
  resetTableQr: (tableId: string) =>
    apiClient.post<TableQrBinding>(`/admin/qr-ordering/tables/${encodeURIComponent(tableId)}/qr/reset`, {}).then(r => r.data),
  rebindCode: (code: string, tableId: string) =>
    apiClient.post<TableQrBinding>(`/admin/qr-ordering/codes/${encodeURIComponent(code)}/rebind`, { tableId }).then(r => r.data),
  listCodes: () => apiClient.get<QrCode[]>('/admin/qr-ordering/codes').then(r => r.data),
  createCode: (data: Omit<QrCode, 'createdAt' | 'updatedAt'>) =>
    apiClient.post<QrCode>('/admin/qr-ordering/codes', data).then(r => r.data),
  updateCode: (code: string, data: Partial<Omit<QrCode, 'code' | 'createdAt' | 'updatedAt'>>) =>
    apiClient.patch<QrCode>(`/admin/qr-ordering/codes/${encodeURIComponent(code)}`, data).then(r => r.data),
  deleteCode: (code: string) =>
    apiClient.delete(`/admin/qr-ordering/codes/${encodeURIComponent(code)}`),
}

export function newId(): string {
  return crypto.randomUUID()
}

export const timecardApi = {
  list: (params?: { from?: number; to?: number; operatorId?: string; page?: number; pageSize?: number }) => {
    const q = new URLSearchParams()
    if (params?.from    != null) q.set('from',       String(params.from))
    if (params?.to      != null) q.set('to',         String(params.to))
    if (params?.operatorId)      q.set('operatorId', params.operatorId)
    if (params?.page    != null) q.set('page',       String(params.page))
    if (params?.pageSize != null) q.set('pageSize',  String(params.pageSize))
    return apiClient.get<TimecardListResponse>(`/admin/timecards?${q}`).then(r => r.data)
  },
  create: (req: { operatorId: string; operatorName?: string; clockInAt: number; clockOutAt?: number | null; clockInNote?: string; clockOutNote?: string }) =>
    apiClient.post<{ id: string }>('/admin/timecards', req).then(r => r.data),
  update: (id: string, req: { clockInAt?: number; clockOutAt?: number | null; clockInNote?: string; clockOutNote?: string }) =>
    apiClient.patch(`/admin/timecards/${id}`, req),
  delete: (id: string) =>
    apiClient.delete(`/admin/timecards/${id}`),
  active: () =>
    apiClient.get<TimecardDto[]>('/admin/timecards/active').then(r => r.data),
  report: (from: number, to: number) =>
    apiClient.get<HoursReportResponse>(`/admin/timecards/report?from=${from}&to=${to}`).then(r => r.data),
  clockIn: (req: { operatorId: string; operatorName?: string; terminalId?: string; note?: string }) =>
    apiClient.post<TimecardDto>('/admin/timecards/clockin', req).then(r => r.data),
  clockOut: (id: string, note?: string) =>
    apiClient.post<TimecardDto>(`/admin/timecards/${id}/clockout`, { note: note ?? '' }).then(r => r.data),
}

export function fmtDate(ms: number) {
  return new Date(ms).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/* ==================================================================== */
/*  Default config                                                        */
/* ==================================================================== */

export const DEFAULT_REGION_CONFIG: RegionConfig = {
  country: 'CN',
  currencyCode: 'CNY',
  currencySymbol: '¥',
  currencyMinorDigits: 2,
  decimalSeparator: '.',
  thousandsSeparator: ',',
  defaultTaxMode: 'EXCLUSIVE',
  availableTaxRates: [],
  rounding: 'HALF_UP',
  serviceChargePermille: 0,
  timeZone: 'Asia/Shanghai',
  locale: 'zh-CN',
  menuPrimaryLanguage: 'zh-CN',
  terminalId: 'pos-1',
  receiptConfig: {
    headerLines: [],
    footerLines: [],
    showTaxId: false,
    taxId: '',
    paperWidthMm: 58,
    fontSizeScale: 1.0,
    printLogo: false,
    openCashDrawer: false,
    printDensity: 80,
    feedLinesAfterPrint: 3,
    kitchenTicketPerOrder: true,
  },
  kdsConfig: {
    stations: ['kitchen'],
    urgentAfterSeconds: 300,
    criticalAfterSeconds: 600,
    fulfillMode: 'FULL_ORDER',
    showAllDayView: false,
    allDayGroupByModifiers: false,
    showOtherStationsProgress: false,
    ticketHeaderMode: 'TABLE_NUMBER',
    consolidateItems: true,
    modifierDisplayMode: 'VERTICAL',
    itemColorCodingEnabled: true,
    expediterPrinterId: null,
    printMode: 'AUTO',
    autoPrintFulfilled: false,
    soundAlertEnabled: true,
    categoryToStation: {},
    orderTypeToStation: {},
    defaultStationId: 'kitchen',
  },
  kioskConfig: {
    autoReturnSeconds: 30,
    qrErrorCorrectionLevel: 'M',
    confirmationQrSizeDp: 200,
    welcomeTitle: '',
    welcomeSubtitle: '',
    completionTitle: '',
    completionSubtitle: '',
    enabledPaymentMethods: ['CARD', 'QR_PAY'],
    showWelcomeScreen: true,
    showDigitalReceiptOption: false,
    showPromotionBanner: false,
    promotionBannerUrl: null,
    accessibilityEnabled: false,
  },
  cdsConfig: {
    enabled: false,
    displayName: 'Display 1',
    welcomeTitle: 'Welcome',
    welcomeSubtitle: '',
    logoUrl: '',
    backgroundImageUrl: '',
    showPromoSlideshow: false,
    promotionImages: [],
    slideshowIntervalSeconds: 5,
    showOrderItems: true,
    showRunningTotal: true,
    showModifiers: true,
    completionTitle: '谢谢惠顾',
    completionSubtitle: '',
    idleTimeoutSeconds: 60,
    displayLocale: 'zh-CN',
  },
  padConfig: {
    ayceEnabled: false,
    maxItemsPerRound: 8,
    minMinutesBetweenRounds: 15,
    totalRoundsLimit: null,
    aycePackages: [],
    idleTimeoutSeconds: 60,
    idlePromoLines: [],
    supportedLocales: ['en'],
    showNutritionInfo: false,
    allowItemNotes: true,
    showRunningTotal: true,
    showOrderHistory: true,
  },
  tipConfig: {
    enabled: true,
    presets: [15, 18, 20, 22],
    calcBase: 'SUBTOTAL',
    enabledOnQr: false,
    enabledOnKiosk: false,
    enabledOnPad: false,
  },
  timeclockConfig: {
    enabled: false,
    requireClockInForOrders: false,
  },
}

/* ==================================================================== */
/*  Region / KDS / Kiosk / Receipt config types                          */
/* ==================================================================== */

export interface TaxRate {
  id: string
  name: string
  ratePermille: number  // e.g. 80 = 8%
  mode: 'INCLUSIVE' | 'EXCLUSIVE'
  rounding: 'HALF_UP' | 'HALF_DOWN' | 'CEILING' | 'FLOOR'
}

export interface ReceiptConfig {
  headerLines: string[]
  footerLines: string[]
  showTaxId: boolean
  taxId: string
  // ---- 打印机硬件配置 ----
  paperWidthMm: number
  fontSizeScale: number
  printLogo: boolean
  openCashDrawer: boolean
  printDensity: number
  feedLinesAfterPrint: number
  kitchenTicketPerOrder: boolean
}

/* ── KDS config (aligned with Toast POS) ─────────────────────────── */
export interface KdsConfig {
  stations: string[]
  urgentAfterSeconds: number
  criticalAfterSeconds: number
  fulfillMode: string              // 'FULL_ORDER' | 'INDIVIDUAL_ITEMS'
  showAllDayView: boolean
  allDayGroupByModifiers: boolean
  showOtherStationsProgress: boolean
  ticketHeaderMode: string         // 'CHECK_NUMBER' | 'TABLE_NUMBER' | 'CUSTOMER_NAME'
  consolidateItems: boolean
  modifierDisplayMode: string      // 'VERTICAL' | 'HORIZONTAL'
  itemColorCodingEnabled: boolean
  expediterPrinterId: string | null
  printMode: string               // 'AUTO' | 'ON_DEMAND'
  autoPrintFulfilled: boolean
  soundAlertEnabled: boolean
  categoryToStation: Record<string, string>
  orderTypeToStation: Record<string, string>
  defaultStationId: string
}

/* ── Kiosk config (aligned with Toast / Square) ─────────────────── */
export interface KioskConfig {
  autoReturnSeconds: number
  qrErrorCorrectionLevel: string  // 'L' | 'M' | 'Q' | 'H'
  confirmationQrSizeDp: number
  welcomeTitle: string
  welcomeSubtitle: string
  completionTitle: string
  completionSubtitle: string
  enabledPaymentMethods: string[]  // e.g. ['CARD', 'QR_PAY']
  showWelcomeScreen: boolean
  showDigitalReceiptOption: boolean
  showPromotionBanner: boolean
  promotionBannerUrl: string | null
  accessibilityEnabled: boolean
}

/* ── CDS (Customer Display Screen) config ──────────────────────── */
export interface CdsConfig {
  enabled: boolean
  displayName: string
  welcomeTitle: string
  welcomeSubtitle: string
  logoUrl: string
  backgroundImageUrl: string
  showPromoSlideshow: boolean
  promotionImages: string[]
  slideshowIntervalSeconds: number
  showOrderItems: boolean
  showRunningTotal: boolean
  showModifiers: boolean
  completionTitle: string
  completionSubtitle: string
  idleTimeoutSeconds: number
  displayLocale: string
}

/* ── Tableside PAD config (Ziosk/Jamezz-inspired) ───────────────── */
export interface AycePackage {
  id: string
  name: string
  description: string
  totalRoundsLimit: number | null
  maxItemsPerRound: number
  minMinutesBetweenRounds: number
  priceMinorUnit: number
}

export interface PadConfig {
  ayceEnabled: boolean
  maxItemsPerRound: number
  minMinutesBetweenRounds: number
  totalRoundsLimit: number | null
  aycePackages: AycePackage[]
  idleTimeoutSeconds: number
  idlePromoLines: string[]
  supportedLocales: string[]
  showNutritionInfo: boolean
  allowItemNotes: boolean
  showRunningTotal: boolean
  showOrderHistory: boolean
}

export interface TimeclockConfig {
  enabled: boolean
  requireClockInForOrders: boolean
}

export interface TimecardDto {
  id: string
  operatorId: string
  operatorName: string
  terminalId: string
  clockInAt: number
  clockOutAt: number | null
  durationMinutes: number | null
  clockInNote: string
  clockOutNote: string
  createdAt: number
}

export interface TimecardListResponse {
  timecards: TimecardDto[]
  total: number
}

export interface TimecardSummaryDto {
  operatorId: string
  operatorName: string
  totalMinutes: number
  shiftsCount: number
}

export interface HoursReportResponse {
  summaries: TimecardSummaryDto[]
  totalMinutes: number
}

export interface TipConfig {
  enabled: boolean
  presets: number[]
  calcBase: 'SUBTOTAL' | 'POST_TAX'
  enabledOnQr: boolean
  enabledOnKiosk: boolean
  enabledOnPad: boolean
}

export interface RegionConfig {
  country?: string
  currencyCode: string
  currencySymbol: string
  currencyMinorDigits: number
  decimalSeparator: string
  thousandsSeparator: string
  defaultTaxMode: 'INCLUSIVE' | 'EXCLUSIVE'
  availableTaxRates: TaxRate[]
  rounding: 'HALF_UP' | 'HALF_DOWN' | 'CEILING' | 'FLOOR'
  /** Web-admin extension. Kept out of core calculation until Android supports merchant defaults. */
  serviceChargePermille: number
  timeZone: string
  locale: string
  menuPrimaryLanguage: string
  terminalId: string
  receiptConfig: ReceiptConfig
  kdsConfig: KdsConfig
  kioskConfig: KioskConfig
  cdsConfig: CdsConfig
  padConfig: PadConfig
  tipConfig: TipConfig
  timeclockConfig: TimeclockConfig
}

export function normalizeRegionConfig(raw: unknown): RegionConfig {
  const input = (raw && typeof raw === 'object') ? raw as Record<string, any> : {}
  const defaults = DEFAULT_REGION_CONFIG
  const kdsInput = (input.kdsConfig && typeof input.kdsConfig === 'object') ? input.kdsConfig as Partial<KdsConfig> : {}
  const kioskInput = (input.kioskConfig && typeof input.kioskConfig === 'object') ? input.kioskConfig as Partial<KioskConfig> : {}
  const receiptInput = (input.receiptConfig && typeof input.receiptConfig === 'object') ? input.receiptConfig as Partial<ReceiptConfig> : {}
  const cdsInput = (input.cdsConfig && typeof input.cdsConfig === 'object') ? input.cdsConfig as Partial<CdsConfig> : {}
  const padInput = (input.padConfig && typeof input.padConfig === 'object') ? input.padConfig as Partial<PadConfig> : {}
  const tipInput = (input.tipConfig && typeof input.tipConfig === 'object') ? input.tipConfig as Partial<TipConfig> : {}
  const timeclockInput = (input.timeclockConfig && typeof input.timeclockConfig === 'object') ? input.timeclockConfig as Partial<TimeclockConfig> : {}
  const legacyTaxes = Array.isArray(input.taxRates) ? input.taxRates : undefined
  const taxes = Array.isArray(input.availableTaxRates) ? input.availableTaxRates : legacyTaxes ?? defaults.availableTaxRates

  return {
    ...defaults,
    ...input,
    currencyMinorDigits: Number(input.currencyMinorDigits ?? input.minorDigits ?? defaults.currencyMinorDigits),
    decimalSeparator: String(input.decimalSeparator ?? defaults.decimalSeparator),
    thousandsSeparator: String(input.thousandsSeparator ?? input.groupingSeparator ?? defaults.thousandsSeparator),
    defaultTaxMode: normalizeTaxMode(input.defaultTaxMode ?? defaults.defaultTaxMode),
    rounding: normalizeRounding(input.rounding ?? defaults.rounding),
    availableTaxRates: taxes.map(normalizeTaxRate),
    serviceChargePermille: Number(input.serviceChargePermille ?? defaults.serviceChargePermille),
    menuPrimaryLanguage: String(input.menuPrimaryLanguage ?? input.locale ?? defaults.menuPrimaryLanguage),
    receiptConfig: { ...defaults.receiptConfig, ...receiptInput },
    kdsConfig: { ...defaults.kdsConfig, ...kdsInput },
    kioskConfig: { ...defaults.kioskConfig, ...kioskInput },
    cdsConfig: { ...defaults.cdsConfig, ...cdsInput },
    padConfig: {
      ...defaults.padConfig,
      ...padInput,
      aycePackages: Array.isArray(padInput.aycePackages) ? padInput.aycePackages.map(normalizeAycePackage) : defaults.padConfig.aycePackages,
      idlePromoLines: Array.isArray(padInput.idlePromoLines) ? padInput.idlePromoLines.map(String) : defaults.padConfig.idlePromoLines,
      supportedLocales: Array.isArray(padInput.supportedLocales) ? padInput.supportedLocales.map(String) : defaults.padConfig.supportedLocales,
    },
    tipConfig: {
      ...defaults.tipConfig,
      ...tipInput,
      presets: Array.isArray(tipInput.presets) ? tipInput.presets.map(Number) : defaults.tipConfig.presets,
      calcBase: tipInput.calcBase === 'POST_TAX' ? 'POST_TAX' : 'SUBTOTAL',
    },
    timeclockConfig: {
      ...defaults.timeclockConfig,
      ...timeclockInput,
    },
  }
}

function normalizeAycePackage(raw: any): AycePackage {
  return {
    id: String(raw?.id ?? crypto.randomUUID()),
    name: String(raw?.name ?? ''),
    description: String(raw?.description ?? ''),
    totalRoundsLimit: raw?.totalRoundsLimit == null ? null : Number(raw.totalRoundsLimit),
    maxItemsPerRound: Number(raw?.maxItemsPerRound ?? 8),
    minMinutesBetweenRounds: Number(raw?.minMinutesBetweenRounds ?? 15),
    priceMinorUnit: Number(raw?.priceMinorUnit ?? 0),
  }
}

function normalizeTaxRate(raw: any): TaxRate {
  return {
    id: String(raw.id ?? crypto.randomUUID()),
    name: String(raw.name ?? 'Tax'),
    ratePermille: Number(raw.ratePermille ?? 0),
    mode: normalizeTaxMode(raw.mode ?? (raw.isInclusive ? 'INCLUSIVE' : 'EXCLUSIVE')),
    rounding: normalizeRounding(raw.rounding ?? 'HALF_UP'),
  }
}

function normalizeTaxMode(value: unknown): 'INCLUSIVE' | 'EXCLUSIVE' {
  return value === 'INCLUSIVE' ? 'INCLUSIVE' : 'EXCLUSIVE'
}

function normalizeRounding(value: unknown): 'HALF_UP' | 'HALF_DOWN' | 'CEILING' | 'FLOOR' {
  return value === 'HALF_DOWN' || value === 'CEILING' || value === 'FLOOR' ? value : 'HALF_UP'
}
