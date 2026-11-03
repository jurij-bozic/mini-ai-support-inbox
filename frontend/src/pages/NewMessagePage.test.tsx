import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import { setCredentials, setUnauthorizedHandler } from '../api/client'
import { NewMessagePage } from './NewMessagePage'

const fetchMock = vi.fn()

function renderForm() {
  vi.stubGlobal('fetch', fetchMock)
  render(
    <MemoryRouter initialEntries={['/new']}>
      <Routes>
        <Route path="/new" element={<NewMessagePage />} />
        <Route path="/messages/:id" element={<h2>Detail page</h2>} />
      </Routes>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

function created(status: string) {
  return new Response(JSON.stringify({ id: 'new-id', status }), { status: 201 })
}

function problem(status: number, body: object) {
  return new Response(JSON.stringify(body), { status })
}

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
  setCredentials(null)
  setUnauthorizedHandler(null)
})

it('rejects blank content on the client without sending a request', async () => {
  const user = renderForm()
  await user.click(screen.getByRole('button', { name: 'Submit' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Message content is required.')
  expect(fetchMock).not.toHaveBeenCalled()
})

it('updates the character counter and rejects over-long content', async () => {
  const user = renderForm()
  const box = screen.getByLabelText('Message')
  await user.type(box, 'abc')
  expect(screen.getByText('3 / 5000')).toBeInTheDocument()

  await user.clear(box)
  await user.click(box)
  await user.paste('x'.repeat(5001))
  expect(screen.getByText('5001 / 5000')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Submit' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('at most 5000')
  expect(fetchMock).not.toHaveBeenCalled()
})

it('rejects a malformed email on the client', async () => {
  const user = renderForm()
  await user.type(screen.getByLabelText('Message'), 'Hello')
  await user.type(screen.getByLabelText(/Customer email/), 'not-an-email')
  await user.click(screen.getByRole('button', { name: 'Submit' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('valid email')
  expect(fetchMock).not.toHaveBeenCalled()
})

it('submits the message and navigates to the detail view', async () => {
  fetchMock.mockResolvedValue(created('DRAFT_READY'))
  const user = renderForm()
  await user.type(screen.getByLabelText('Message'), 'Where is my order?')
  await user.type(screen.getByLabelText(/Customer name/), ' Ana ')
  await user.click(screen.getByRole('button', { name: 'Submit' }))

  expect(await screen.findByRole('heading', { name: 'Detail page' })).toBeInTheDocument()
  const [url, init] = fetchMock.mock.calls[0]
  expect(String(url)).toContain('/api/messages')
  expect(init.method).toBe('POST')
  expect(JSON.parse(init.body)).toEqual({
    content: 'Where is my order?',
    customerName: 'Ana',
    customerEmail: null,
  })
})

it('still navigates to the detail view when the AI step failed', async () => {
  fetchMock.mockResolvedValue(created('AI_FAILED'))
  const user = renderForm()
  await user.type(screen.getByLabelText('Message'), 'Hello')
  await user.click(screen.getByRole('button', { name: 'Submit' }))
  expect(await screen.findByRole('heading', { name: 'Detail page' })).toBeInTheDocument()
})

it('shows server field errors next to the field', async () => {
  fetchMock.mockResolvedValue(
    problem(400, { title: 'Validation failed', errors: { customerEmail: 'must be a well-formed email address' } }),
  )
  const user = renderForm()
  await user.type(screen.getByLabelText('Message'), 'Hello')
  await user.click(screen.getByRole('button', { name: 'Submit' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('must be a well-formed email address')
  expect(screen.getByLabelText(/Customer email/)).toHaveAttribute('aria-invalid', 'true')
})

it('shows a general error for non-field failures and re-enables the form', async () => {
  fetchMock.mockResolvedValue(problem(429, { title: 'Too Many Requests', detail: 'Rate limit exceeded.' }))
  const user = renderForm()
  await user.type(screen.getByLabelText('Message'), 'Hello')
  await user.click(screen.getByRole('button', { name: 'Submit' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Rate limit exceeded.')
  expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled()
})
