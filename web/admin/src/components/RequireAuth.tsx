import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import type { ReactNode } from 'react'

export default function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user && import.meta.env.VITE_MOCK_AUTH !== 'true') {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}
