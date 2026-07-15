import { apiClient } from './client'

export type AiObservationSeverity = 'positive' | 'warning' | 'neutral'
export type AiActionPriority = 'high' | 'medium' | 'low'

export interface OperatingInsightRequest {
  fromMs: number
  toMs: number
  locale: string
}

export interface OperatingInsightResponse {
  generatedAt: number
  model: string
  period: { fromMs: number; toMs: number }
  snapshot: {
    orderCount: number
    netRevenueMinorUnit: number
    averageOrderValueMinorUnit: number
    guestCount: number
  }
  headline: string
  summary: string
  observations: Array<{
    severity: AiObservationSeverity
    title: string
    detail: string
    evidenceKeys: string[]
  }>
  actions: Array<{
    priority: AiActionPriority
    title: string
    reason: string
  }>
}

export const aiInsightApi = {
  generate: (request: OperatingInsightRequest) =>
    apiClient
      .post<OperatingInsightResponse>('/admin/ai/operating-insight', request, { timeout: 20_000 })
      .then(response => response.data),
}
