import { apiClient } from './client'

export interface RestaurantTable {
  id: string
  name: string
  capacity: number
  minCapacity?: number
  section: string
  sectionId?: string
  active: boolean
  // spatial layout (from floor plan editor)
  x?: number
  y?: number
  w?: number
  h?: number
  shape?: 'square' | 'round' | 'rect'
}

export const tableApi = {
  list: () => apiClient.get<RestaurantTable[]>('/admin/tables').then(r => r.data),
  create: (data: Omit<RestaurantTable, 'id'>) =>
    apiClient.post<RestaurantTable>('/admin/tables', data).then(r => r.data),
  update: (id: string, patch: Partial<RestaurantTable>) =>
    apiClient.patch(`/admin/tables/${id}`, patch),
  delete: (id: string) => apiClient.delete(`/admin/tables/${id}`),
}
