import { useState, useCallback } from 'react'
import { loginWithPassword, type LoginResponse } from '../api/auth'

interface AuthState {
  user: LoginResponse | null
  loading: boolean
  error: string | null
}

function loadStoredUser(): LoginResponse | null {
  try {
    const raw = localStorage.getItem('pos_admin_user')
    return raw ? (JSON.parse(raw) as LoginResponse) : null
  } catch {
    return null
  }
}

export function useAuth() {
  const [state, setState] = useState<AuthState>({
    user: loadStoredUser(),
    loading: false,
    error: null,
  })

  const login = useCallback(async (email: string, password: string) => {
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const user = await loginWithPassword(email, password)
      localStorage.setItem('pos_admin_token', user.token)
      localStorage.setItem('pos_admin_user', JSON.stringify(user))
      setState({ user, loading: false, error: null })
      return true
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? '登录失败，请检查邮箱和密码'
      setState((s) => ({ ...s, loading: false, error: msg }))
      return false
    }
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('pos_admin_token')
    localStorage.removeItem('pos_admin_user')
    setState({ user: null, loading: false, error: null })
  }, [])

  return { ...state, login, logout }
}
