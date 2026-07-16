import { apiClient } from './client'

export interface GroupBuyingRedemption {
  id: string
  provider: 'DOUYIN' | 'MEITUAN'
  maskedCode: string
  title: string
  orderId: string
  operatorId: string
  redeemedAmountMinorUnit: number
  providerReference: string
  status: string
  demo: boolean
  createdAt: number
}

export const groupBuyingApi = {
  listRedemptions: () =>
    apiClient.get<GroupBuyingRedemption[]>('/admin/group-buying-redemptions').then(response => response.data),
}
