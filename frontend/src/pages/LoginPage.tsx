import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { ApiError } from '../api/errors'
import { useAuth } from '../auth/useAuth'

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) return 'Invalid username or password.'
  if (error instanceof Error) return error.message
  return 'Login failed.'
}

export function LoginPage() {
  const { username: loggedIn, login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [slow, setSlow] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (loggedIn) return <Navigate to="/" replace />

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    setSlow(false)
    setError(null)
    try {
      await login({ username, password }, () => setSlow(true))
      navigate('/', { replace: true })
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  return (
    <main>
      <h1>Mini AI Support Inbox</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <button type="submit" disabled={pending}>
          {pending ? 'Signing in…' : 'Sign in'}
        </button>
        {slow && <p role="status">The server is waking up, this can take up to a minute…</p>}
        {error && <p role="alert">{error}</p>}
      </form>
    </main>
  )
}
