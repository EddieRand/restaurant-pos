// Data-driven CDS model. Money values are whole-currency numbers (e.g. 38.88) for the
// mock layer; the real POS stores minor units (cents) — a future adapter will convert.

export type OrderType = 'Dine In' | 'Takeaway' | 'Delivery'

export interface CdsStore {
  name: string
  /** Optional logo URL; when absent a brand tile placeholder is shown. */
  logoUrl?: string
}

export interface CdsLineItem {
  qty: number
  name: string
  /** Modifiers / special instructions, shown under the item name. */
  modifiers?: string
  amount: number
}

export interface CdsTotals {
  subtotal: number
  discount: number
  tax: number
  serviceCharge: number
  tip?: number
  total: number
}

export interface CdsOrder {
  number: string
  type: OrderType
  tableLabel?: string
  items: CdsLineItem[]
  totals: CdsTotals
}

export interface CdsTipOption {
  percent: number
  label: string
  amount: number
}

export interface CdsPayment {
  totalPaid: number
  change: number
}

export type ReceiptMethod = 'email' | 'text'

/** Display copy + toggles configured in the admin 客显管理 page. Drives the CDS screens. */
export interface CdsDisplayConfig {
  welcomeTitle: string
  welcomeSubtitle: string
  completionTitle: string
  completionSubtitle: string
  showOrderItems: boolean
  showRunningTotal: boolean
  showModifiers: boolean
}

export const DEFAULT_DISPLAY_CONFIG: CdsDisplayConfig = {
  welcomeTitle: 'Welcome!',
  welcomeSubtitle: 'Please review your order here.',
  completionTitle: 'Payment successful',
  completionSubtitle: 'Thank you. Your payment has been completed.',
  showOrderItems: true,
  showRunningTotal: true,
  showModifiers: true,
}

export type CdsPage =
  | 'welcome'
  | 'order'
  | 'tip'
  | 'processing'
  | 'success'
  | 'receipt'
