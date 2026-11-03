import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import App from '../App'
import { setCredentials, setUnauthorizedHandler } from '../api/client'
import { AuthProvider } from '../auth/AuthContext'

const fetchMock = vi.fn()

function renderApp() {
  vi.stubGlobal('fetch', fetchMock)
  render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function submit() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Username'), 'admin')
  await user.type(screen.getByLabelText('Password'), 'secret')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
  setCredentials(null)
  setUnauthorizedHandler(null)
})

it('logs in with valid credentials and shows the message list', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ username: 'admin' }), { status: 200 }))
  renderApp()
  await submit()
  expect(await screen.findByRole('heading', { name: 'Messages' })).toBeInTheDocument()
})

it('shows an error and stays on login for invalid credentials', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ title: 'Unauthorized' }), { status: 401 }))
  renderApp()
  await submit()
  expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
  expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
})

it('shows a network error message when the server is unreachable', async () => {
  fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
  renderApp()
  await submit()
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server.')
})
