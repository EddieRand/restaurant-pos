import { apiClient } from './client'

export interface LoginResponse {
  token: string
  userId: string
  role: string
  displayName: string
}

export async function loginWithPassword(email: string, password: string): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/auth/login/password', { email, password })
  return data
}
