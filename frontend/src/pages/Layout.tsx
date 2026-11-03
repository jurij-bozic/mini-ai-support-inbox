import { Link, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'

export function Layout() {
  const { username, logout } = useAuth()
  return (
    <>
      <header>
        <strong>Mini AI Support Inbox</strong>
        <nav>
          <Link to="/">Messages</Link> <Link to="/new">New message</Link>
        </nav>
        <span>{username}</span> <button onClick={logout}>Log out</button>
      </header>
      <main>
        <Outlet />
      </main>
    </>
  )
}
