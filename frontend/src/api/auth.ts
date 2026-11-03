import { apiFetch, type Credentials } from './client'

export interface Me {
  username: string
}

/** Verifies the given credentials; a wrong login must not trigger the global 401 handler. */
export function fetchMe(credentials: Credentials, onSlow?: () => void): Promise<Me> {
  return apiFetch<Me>('/api/auth/me', { credentials, onSlow, handleUnauthorized: false })
}
