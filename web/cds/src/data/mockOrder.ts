import type { CdsOrder, CdsPayment, CdsTipOption } from '../types'

// Mock order mirrors the reference design (#1042). Kept isolated from UI so it can be
// swapped for live POS order state later. Totals follow the POS formula:
// total = subtotal + tax + serviceCharge + tip - discount.
export const mockOrder: CdsOrder = {
  number: '1042',
  type: 'Dine In',
  tableLabel: 'Table 07',
  items: [
    { qty: 2, name: 'Signature Combo', modifiers: 'No onions, Extra sauce', amount: 23.98 },
    { qty: 1, name: 'Side Salad', modifiers: 'No croutons', amount: 4.49 },
    { qty: 1, name: 'Sparkling Water', modifiers: 'Lemon', amount: 2.79 },
    { qty: 1, name: 'Dessert Item', modifiers: 'Gift wrap', amount: 5.99 },
  ],
  totals: {
    subtotal: 37.25,
    discount: 2.5,
    tax: 2.63,
    serviceCharge: 1.5,
    total: 38.88,
  },
}

// Tip presets follow the POS TipConfig defaults (15/18/20). The reference tip page
// computes against a smaller pre-tip base (Total $11.91 -> Total with Tip $14.30).
export const mockTipBase = 11.91

export const mockTipOptions: CdsTipOption[] = [
  { percent: 15, label: 'Good', amount: 1.79 },
  { percent: 18, label: 'Great', amount: 2.15 },
  { percent: 20, label: 'Best', amount: 2.39 },
]

export const mockPayment: CdsPayment = {
  totalPaid: 38.88,
  change: 0.0,
}
