import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it } from 'vitest'
import App from './App'
import { AuthProvider } from './auth/AuthContext'

it('redirects unauthenticated users to the login view', () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
  expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
})
