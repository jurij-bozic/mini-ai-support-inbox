import { Navigate, Outlet } from 'react-router'
import { useAuth } from './useAuth'

export function RequireAuth() {
  const { username } = useAuth()
  return username ? <Outlet /> : <Navigate to="/login" replace />
}
