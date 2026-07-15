import { apiClient } from './client'

export interface Channel {
  id: string
  name: string
  sortOrder: number
  enabled: boolean
  color: string
}

export const channelApi = {
  list: () => apiClient.get<Channel[]>('/admin/channels').then(r => r.data),
  create: (data: Omit<Channel, 'enabled'>) => apiClient.post('/admin/channels', data),
  update: (id: string, patch: Partial<Omit<Channel, 'id'>>) => apiClient.patch(`/admin/channels/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/channels/${id}`),
}

export interface MenuCategory {
  id: string
  name: string
  sortOrder: number
  activeFrom?: string   // "HH:MM" — undefined = all day
  activeTo?: string     // "HH:MM" — undefined = all day
  daysOfWeek?: number[] // 0=Sun…6=Sat — undefined/empty = every day
}

export const categoryApi = {
  list: () => apiClient.get<MenuCategory[]>('/admin/menu-categories').then(r => r.data),
  create: (data: Omit<MenuCategory, 'id'>) => apiClient.post<MenuCategory>('/admin/menu-categories', data).then(r => r.data),
  update: (id: string, patch: Partial<Omit<MenuCategory, 'id'>>) => apiClient.patch(`/admin/menu-categories/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/menu-categories/${id}`),
}

export interface MenuItem {
  id: string
  names: string           // JSON: {"en":"Burger","zh":"汉堡"}
  description?: string    // JSON: {"zh":"...", "en":"..."} — optional
  priceMinorUnit: number
  taxRateId?: string
  categoryId: string
  course: number
  isSoldOut: boolean
  imageUrl?: string
  allergens: string       // pipe-separated
  modifierGroups?: ModifierGroup[]
  availableChannels?: string[]  // empty/undefined = all channels
  stockCount?: number           // null/undefined = unlimited; 0 = auto sold-out
  updatedAt: number
}

export interface Combo {
  id: string
  names: string
  imageUrl?: string
  comboPriceMinorUnit: number
  taxRateId?: string
  isActive: boolean
  components: ComboComponent[]
}

export interface ComboComponent {
  id: string
  comboId?: string
  menuItemId: string
  quantity: number
  sortOrder: number
}

export interface ModifierOption {
  id: string
  name: string              // JSON: {"zh":"加蛋","en":"Add Egg"}
  priceAdjustMinorUnit: number
  isDefault: boolean
  sortOrder: number
}

export interface ModifierGroup {
  id: string
  name: string              // JSON: {"zh":"份量","en":"Size"}
  selectionType: 'SINGLE' | 'MULTIPLE'
  required: boolean
  minSelect: number
  maxSelect: number         // 0 = unlimited
  options: ModifierOption[]
}

export interface Coupon {
  id: string
  code: string
  type: string
  value: number
  expiresAt: number
  isActive: boolean
}

// ── Menu Items ────────────────────────────────────────────────────────────────

export const menuApi = {
  list: () => apiClient.get<MenuItem[]>('/admin/menu').then(r => r.data),

  create: (data: Omit<MenuItem, 'isSoldOut' | 'updatedAt'>) =>
    apiClient.post('/admin/menu', data),

  update: (id: string, patch: Partial<MenuItem>) =>
    apiClient.patch(`/admin/menu/${id}`, patch),

  delete: (id: string) => apiClient.delete(`/admin/menu/${id}`),

  bulkAvailability: (ids: string[], isSoldOut: boolean) =>
    apiClient.post('/admin/menu/bulk-availability', { ids, isSoldOut }),
}

// ── Combos ────────────────────────────────────────────────────────────────────

export const comboApi = {
  list: () => apiClient.get<Combo[]>('/admin/combos').then(r => r.data),

  create: (data: Omit<Combo, 'isActive'>) =>
    apiClient.post('/admin/combos', data),

  update: (id: string, patch: Partial<Combo>) =>
    apiClient.patch(`/admin/combos/${id}`, patch),
}

// ── Menu Profiles (time-based & channel-based menus) ─────────────────────────

export interface MenuProfile {
  id: string
  name: string
  enabled: boolean
  startTime?: string    // "HH:MM" — undefined = all day
  endTime?: string      // "HH:MM" — undefined = all day
  daysOfWeek?: number[] // 0=Sun…6=Sat — undefined/empty = every day
}

export const menuProfileApi = {
  list: () => apiClient.get<MenuProfile[]>('/admin/menu-profiles').then(r => r.data),
  create: (data: Omit<MenuProfile, 'id'>) =>
    apiClient.post<MenuProfile>('/admin/menu-profiles', data).then(r => r.data),
  update: (id: string, patch: Partial<Omit<MenuProfile, 'id'>>) =>
    apiClient.patch(`/admin/menu-profiles/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/menu-profiles/${id}`),
  getItems: (id: string) =>
    apiClient.get<string[]>(`/admin/menu-profiles/${id}/items`).then(r => r.data),
  setItems: (id: string, menuItemIds: string[]) =>
    apiClient.put(`/admin/menu-profiles/${id}/items`, { menuItemIds }),
}

// ── Modifier Groups ───────────────────────────────────────────────────────────

export const modifierGroupApi = {
  list: () => apiClient.get<ModifierGroup[]>('/admin/modifier-groups').then(r => r.data),
  create: (data: Omit<ModifierGroup, 'id'>) =>
    apiClient.post<ModifierGroup>('/admin/modifier-groups', data).then(r => r.data),
  update: (id: string, patch: Partial<Omit<ModifierGroup, 'id'>>) =>
    apiClient.patch(`/admin/modifier-groups/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/modifier-groups/${id}`),
}

// ── Coupons ───────────────────────────────────────────────────────────────────

export const couponApi = {
  list: () => apiClient.get<Coupon[]>('/admin/coupons').then(r => r.data),

  create: (data: Omit<Coupon, 'isActive'>) =>
    apiClient.post('/admin/coupons', data),

  update: (id: string, patch: Partial<Coupon>) =>
    apiClient.patch(`/admin/coupons/${id}`, patch),

  delete: (id: string) => apiClient.delete(`/admin/coupons/${id}`),
}

// ── Helpers ───────────────────────────────────────────────────────────────────

export function parseName(names: string, lang = getMenuPrimaryLanguage()): string {
  try {
    const obj = JSON.parse(names) as Record<string, string>
    const prefix = lang.split('-')[0]
    const enFallback = obj['en-US'] ?? obj['en']
    const zhFallback = obj['zh-CN'] ?? obj['zh']
    // Primary lang → prefix → en → zh → any value
    return (
      obj[lang] ??
      obj[prefix] ??
      (prefix !== 'en' ? enFallback : undefined) ??
      (prefix !== 'zh' ? zhFallback : undefined) ??
      enFallback ??
      zhFallback ??
      Object.values(obj).find(v => v) ??
      '(未命名)'
    )
  } catch {
    return names
  }
}

function getMenuPrimaryLanguage(): string {
  try {
    const raw = localStorage.getItem('pos_menu_primary_language')
    return raw ?? 'zh-CN'
  } catch {
    return 'zh-CN'
  }
}

export function fmtPrice(minor: number): string {
  return `¥${(minor / 100).toFixed(2)}`
}

export function newId(): string {
  return crypto.randomUUID()
}
