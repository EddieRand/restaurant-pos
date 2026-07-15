import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
})

// Attach stored JWT to every request
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('pos_admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 401 = token 过期/无效 → 清除登录态并跳转
// 403 = 无此接口权限 → 不登出，由页面自行展示提示
apiClient.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status
    const isAiInsightRequest = err.config?.url === '/admin/ai/operating-insight'
    if (status === 401 && !isAiInsightRequest && import.meta.env.VITE_MOCK_AUTH !== 'true') {
      localStorage.removeItem('pos_admin_token')
      localStorage.removeItem('pos_admin_user')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  },
)
