import { apiClient } from './client'

export interface Customer {
  id: string
  name: string
  phone: string
  email?: string
  gender?: 'M' | 'F' | 'OTHER'
  birthday?: string              // "MM-DD"
  registeredAt: number
  lastVisitAt: number
  totalVisits: number
  totalSpendMinorUnit: number
  loyaltyPoints: number
  membershipTierId?: string
  tags: string[]
  notes?: string
}

export interface MembershipTier {
  id: string
  name: string
  minPoints: number
  benefits: string[]
  color: string                  // e.g. 'gray' | 'blue' | 'amber' | 'purple'
  discountPermille: number       // 0 = none
  pointsMultiplier: number       // 100 = 1x, 200 = 2x
}

export interface LoyaltyTransaction {
  id: string
  customerId: string
  orderId?: string
  type: 'EARN' | 'REDEEM' | 'ADJUST' | 'EXPIRE'
  points: number
  description: string
  createdAt: number
}

export interface Campaign {
  id: string
  name: string
  type: 'COUPON_PUSH' | 'IN_APP_NOTICE'
  targetSegment: 'ALL' | 'TIER' | 'BIRTHDAY_MONTH' | 'INACTIVE_30D' | 'INACTIVE_60D'
  targetTierId?: string
  couponId?: string
  message: string
  scheduledAt?: number
  status: 'DRAFT' | 'SCHEDULED' | 'SENT'
  sentCount: number
  createdAt: number
}

export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'SEATED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'
export type ReservationSource = 'ONLINE' | 'PHONE' | 'WALK_IN' | 'CONCIERGE'
export type GuestTag = 'BIRTHDAY' | 'ANNIVERSARY' | 'VIP' | 'FIRST_TIME'

export interface Reservation {
  id: string
  customerName: string
  phone: string
  customerId?: string
  partySize: number
  date: string                      // "YYYY-MM-DD"
  time: string                      // "HH:MM"
  tableId?: string
  status: ReservationStatus
  notes?: string                    // guest-facing notes
  internalNotes?: string            // staff-only
  createdAt: number
  // enhanced
  shiftId?: string
  source?: ReservationSource
  guestTags?: GuestTag[]
  confirmationCode?: string
  estimatedDurationMinutes?: number
  depositAmount?: number
  depositPaid?: boolean
  reminderSentAt?: number
  seatedAt?: number
  completedAt?: number
}

export interface WaitlistEntry {
  id: string
  customerName: string
  phone: string
  customerId?: string
  partySize: number
  requestedAt: number
  quotedWaitMinutes: number
  status: 'WAITING' | 'NOTIFIED' | 'SEATED' | 'CANCELLED' | 'EXPIRED'
  notifiedAt?: number
  expiresAt?: number               // auto-expire N min after notify
  tableId?: string
  preferences: string[]            // 'INDOOR'|'OUTDOOR'|'WINDOW'|'QUIET'
  seatedAt?: number
  notes?: string
}

// ── Customers ─────────────────────────────────────────────────────────────────

export const customerApi = {
  list: (params?: { q?: string; tierId?: string; page?: number }) => {
    const qs = new URLSearchParams()
    if (params?.q) qs.set('q', params.q)
    if (params?.tierId) qs.set('tierId', params.tierId)
    if (params?.page != null) qs.set('page', String(params.page))
    const query = qs.toString()
    return apiClient.get<Customer[]>(`/admin/customers${query ? `?${query}` : ''}`).then(r => r.data)
  },
  get: (id: string) => apiClient.get<Customer>(`/admin/customers/${id}`).then(r => r.data),
  create: (data: Omit<Customer, 'id' | 'registeredAt' | 'lastVisitAt' | 'totalVisits' | 'totalSpendMinorUnit' | 'loyaltyPoints'>) =>
    apiClient.post<Customer>('/admin/customers', data).then(r => r.data),
  update: (id: string, patch: Partial<Customer>) =>
    apiClient.patch(`/admin/customers/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/customers/${id}`),
}

// ── Loyalty / Tiers ───────────────────────────────────────────────────────────

export const loyaltyApi = {
  getTiers: () => apiClient.get<MembershipTier[]>('/admin/loyalty/tiers').then(r => r.data),
  createTier: (data: Omit<MembershipTier, 'id'>) =>
    apiClient.post<MembershipTier>('/admin/loyalty/tiers', data).then(r => r.data),
  updateTier: (id: string, patch: Partial<Omit<MembershipTier, 'id'>>) =>
    apiClient.patch(`/admin/loyalty/tiers/${id}`, patch),
  deleteTier: (id: string) => apiClient.delete(`/admin/loyalty/tiers/${id}`),
  getTransactions: (customerId: string) =>
    apiClient.get<LoyaltyTransaction[]>(`/admin/customers/${customerId}/loyalty-transactions`).then(r => r.data),
  adjustPoints: (customerId: string, delta: number, description: string) =>
    apiClient.post(`/admin/customers/${customerId}/adjust-points`, { delta, description }),
}

// ── Campaigns ─────────────────────────────────────────────────────────────────

export const campaignApi = {
  list: () => apiClient.get<Campaign[]>('/admin/campaigns').then(r => r.data),
  create: (data: Omit<Campaign, 'id' | 'sentCount' | 'createdAt'>) =>
    apiClient.post<Campaign>('/admin/campaigns', data).then(r => r.data),
  update: (id: string, patch: Partial<Campaign>) =>
    apiClient.patch(`/admin/campaigns/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/campaigns/${id}`),
  send: (id: string) => apiClient.post(`/admin/campaigns/${id}/send`, {}),
}

// ── Reservations ──────────────────────────────────────────────────────────────

export const reservationApi = {
  list: (params?: { date?: string; status?: string; shiftId?: string }) => {
    const qs = new URLSearchParams()
    if (params?.date) qs.set('date', params.date)
    if (params?.status) qs.set('status', params.status)
    if (params?.shiftId) qs.set('shiftId', params.shiftId)
    const query = qs.toString()
    return apiClient.get<Reservation[]>(`/admin/reservations${query ? `?${query}` : ''}`).then(r => r.data)
  },
  create: (data: Omit<Reservation, 'id' | 'createdAt'>) =>
    apiClient.post<Reservation>('/admin/reservations', data).then(r => r.data),
  update: (id: string, patch: Partial<Reservation>) =>
    apiClient.patch(`/admin/reservations/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/reservations/${id}`),
}

// ── Waitlist ───────────────────────────────────────────────────────────────────

export const waitlistApi = {
  list: (params?: { status?: string }) => {
    const qs = new URLSearchParams()
    if (params?.status) qs.set('status', params.status)
    const q = qs.toString()
    return apiClient.get<WaitlistEntry[]>(`/admin/waitlist${q ? `?${q}` : ''}`).then(r => r.data)
  },
  create: (data: Omit<WaitlistEntry, 'id'>) =>
    apiClient.post<WaitlistEntry>('/admin/waitlist', data).then(r => r.data),
  update: (id: string, patch: Partial<WaitlistEntry>) =>
    apiClient.patch(`/admin/waitlist/${id}`, patch),
  notify: (id: string) => apiClient.post(`/admin/waitlist/${id}/notify`, {}),
  seat: (id: string, tableId?: string) =>
    apiClient.post(`/admin/waitlist/${id}/seat`, { tableId }),
  cancel: (id: string) => apiClient.post(`/admin/waitlist/${id}/cancel`, {}),
}

// ── Gift Cards (礼品卡/储值卡) ───────────────────────────────────────────────────

export interface GiftCard {
  id: string
  code: string
  balanceMinorUnit: number
  customerId?: string | null
  isActive: boolean
  createdAt: number
  updatedAt: number
}

export interface GiftCardTransaction {
  id: string
  giftCardId: string
  type: 'ISSUE' | 'TOPUP' | 'REDEEM' | 'ADJUST'
  amountMinorUnit: number
  orderId?: string | null
  operatorId: string
  note: string
  createdAt: number
}

export const giftCardApi = {
  list: (code?: string) =>
    apiClient.get<GiftCard[]>(`/admin/gift-cards${code ? `?code=${encodeURIComponent(code)}` : ''}`).then(r => r.data),

  create: (data: { code: string; initialBalanceMinorUnit?: number; customerId?: string | null }) =>
    apiClient.post<{ id: string; code: string }>('/admin/gift-cards', data).then(r => r.data),

  transactions: (id: string) =>
    apiClient.get<GiftCardTransaction[]>(`/admin/gift-cards/${id}/transactions`).then(r => r.data),

  setActive: (id: string, isActive: boolean) =>
    apiClient.patch(`/admin/gift-cards/${id}`, { isActive }),

  topUp: (id: string, amountMinorUnit: number, note?: string) =>
    apiClient.post(`/admin/gift-cards/${id}/topup`, { amountMinorUnit, note: note ?? '' }),

  redeem: (code: string, amountMinorUnit: number, orderId?: string, operatorId?: string, note?: string) =>
    apiClient.post<{ ok: boolean; remainingBalanceMinorUnit: number }>('/admin/gift-cards/redeem', {
      code, amountMinorUnit, orderId, operatorId: operatorId ?? '', note: note ?? '',
    }).then(r => r.data),
}
