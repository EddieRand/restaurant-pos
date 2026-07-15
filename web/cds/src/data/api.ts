import { DEFAULT_DISPLAY_CONFIG, type CdsDisplayConfig, type CdsOrder, type CdsPage, type CdsPayment, type CdsStore, type OrderType } from '../types'

// Live CDS state from the POS server (GET /public/cds/state). Served same-origin in
// production (server static), proxied in dev. Money fields already arrive whole-currency.

interface ServerCdsState {
  phase: string
  store: { name: string; logoUrl?: string }
  currencySymbol?: string
  minorDigits?: number
  config?: Partial<CdsDisplayConfig>
  order: {
    number: string
    type: string
    tableLabel?: string | null
    items: { qty: number; name: string; modifiers?: string | null; amount: number }[]
    totals: { subtotal: number; discount: number; tax: number; serviceCharge: number; tip: number; total: number }
  } | null
  payment: { totalPaid: number; change: number } | null
}

export interface LiveCdsState {
  page: CdsPage
  store: CdsStore
  order: CdsOrder | null
  payment: CdsPayment | null
  currencySymbol: string
  minorDigits: number
  config: CdsDisplayConfig
}

const PHASE_TO_PAGE: Record<string, CdsPage> = {
  WELCOME: 'welcome',
  ORDER: 'order',
  TIP: 'tip',
  PROCESSING: 'processing',
  SUCCESS: 'success',
  RECEIPT: 'receipt',
}

/** Fetches live CDS state; returns null when the backend is unreachable (use mock fallback). */
export async function fetchCdsState(signal?: AbortSignal): Promise<LiveCdsState | null> {
  try {
    const res = await fetch('/public/cds/state', { signal, headers: { Accept: 'application/json' } })
    if (!res.ok) return null
    const dto = (await res.json()) as ServerCdsState
    return {
      page: PHASE_TO_PAGE[dto.phase] ?? 'welcome',
      store: { name: dto.store?.name || 'Store Name', logoUrl: dto.store?.logoUrl || undefined },
      order: dto.order ? mapOrder(dto.order) : null,
      payment: dto.payment ?? null,
      currencySymbol: dto.currencySymbol || '$',
      minorDigits: typeof dto.minorDigits === 'number' ? dto.minorDigits : 2,
      config: { ...DEFAULT_DISPLAY_CONFIG, ...(dto.config ?? {}) },
    }
  } catch {
    return null
  }
}

function mapOrder(o: NonNullable<ServerCdsState['order']>): CdsOrder {
  return {
    number: o.number,
    type: (o.type as OrderType) ?? 'Dine In',
    tableLabel: o.tableLabel ?? undefined,
    items: o.items.map((i) => ({
      qty: i.qty,
      name: i.name,
      modifiers: i.modifiers ?? undefined,
      amount: i.amount,
    })),
    totals: o.totals,
  }
}
