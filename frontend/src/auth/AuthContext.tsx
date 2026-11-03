import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe } from '../api/auth'
import { setCredentials, setUnauthorizedHandler, type Credentials } from '../api/client'
import { AuthContext } from './useAuth'

// Credentials live in memory only (no localStorage/sessionStorage); a refresh logs out by design.
export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null)

  const logout = useCallback(() => {
    setCredentials(null)
    setUsername(null)
  }, [])

  const login = useCallback(async (credentials: Credentials, onSlow?: () => void) => {
    const me = await fetchMe(credentials, onSlow)
    setCredentials(credentials)
    setUsername(me.username)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  const value = useMemo(() => ({ username, login, logout }), [username, login, logout])
  return <AuthContext value={value}>{children}</AuthContext>
}
