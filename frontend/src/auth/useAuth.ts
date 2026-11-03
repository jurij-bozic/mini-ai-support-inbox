import { createContext, useContext } from 'react'
import type { Credentials } from '../api/client'

export interface AuthContextValue {
  username: string | null
  login: (credentials: Credentials, onSlow?: () => void) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
