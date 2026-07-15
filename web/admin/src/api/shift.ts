import { apiClient } from './client'

export interface TurnTimeRule {
  partySize: number
  minutes: number
}

export interface Shift {
  id: string
  name: string
  startTime: string                 // "HH:MM"
  endTime: string
  daysOfWeek: number[]              // 0=Sun…6=Sat; empty = every day
  maxCovers: number
  coverGoal: number
  defaultTurnTimeMinutes: number
  turnTimeByPartySize: TurnTimeRule[]
  slotIntervalMinutes: 15 | 30
  bookingCutoffMinutes: number      // stop accepting X min before shift
  enabled: boolean
}

export interface SpecialDay {
  id: string
  date: string                      // "YYYY-MM-DD"
  label: string
  maxCoversOverride?: number
  closed: boolean
}

export interface SlotAvailability {
  time: string
  covers: number
  capacity: number
  available: boolean
}

export const shiftApi = {
  list: () => apiClient.get<Shift[]>('/admin/shifts').then(r => r.data),
  create: (data: Omit<Shift, 'id'>) =>
    apiClient.post<Shift>('/admin/shifts', data).then(r => r.data),
  update: (id: string, patch: Partial<Shift>) =>
    apiClient.patch(`/admin/shifts/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/shifts/${id}`),
  getAvailability: (shiftId: string, date: string) =>
    apiClient
      .get<SlotAvailability[]>(`/admin/shifts/${shiftId}/availability?date=${date}`)
      .then(r => r.data),
}

export const specialDayApi = {
  list: (params?: { from?: string; to?: string }) => {
    const qs = new URLSearchParams()
    if (params?.from) qs.set('from', params.from)
    if (params?.to) qs.set('to', params.to)
    const q = qs.toString()
    return apiClient.get<SpecialDay[]>(`/admin/special-days${q ? `?${q}` : ''}`).then(r => r.data)
  },
  create: (data: Omit<SpecialDay, 'id'>) =>
    apiClient.post<SpecialDay>('/admin/special-days', data).then(r => r.data),
  update: (id: string, patch: Partial<SpecialDay>) =>
    apiClient.patch(`/admin/special-days/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/special-days/${id}`),
}
