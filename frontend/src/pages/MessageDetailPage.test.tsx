import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import { setCredentials, setUnauthorizedHandler } from '../api/client'
import type { MessageDetail } from '../api/messages'
import { MessageDetailPage } from './MessageDetailPage'

const fetchMock = vi.fn()

function message(overrides: Partial<MessageDetail> = {}): MessageDetail {
  return {
    id: 'm1',
    customerName: 'Ana',
    customerEmail: null,
    content: 'Where is my order?',
    category: 'QUESTION',
    status: 'DRAFT_READY',
    aiDraft: 'Hello Ana, we will check.',
    finalReply: null,
    aiErrorCode: null,
    createdAt: '2026-11-03T09:00:00Z',
    updatedAt: '2026-11-03T09:00:00Z',
    approvedAt: null,
    version: 0,
    canRegenerate: ['DRAFT_READY', 'AI_FAILED'].includes(overrides.status ?? 'DRAFT_READY'),
    ...overrides,
  }
}

function json(body: object, status = 200) {
  return new Response(JSON.stringify(body), { status })
}

function renderDetail(initial: MessageDetail) {
  fetchMock.mockResolvedValueOnce(json(initial))
  vi.stubGlobal('fetch', fetchMock)
  render(
    <MemoryRouter initialEntries={['/messages/m1']}>
      <Routes>
        <Route path="/messages/:id" element={<MessageDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

function lastRequest() {
  const [url, init] = fetchMock.mock.calls.at(-1)!
  return { url: String(url), method: init.method, body: init.body ? JSON.parse(init.body) : undefined }
}

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
  setCredentials(null)
  setUnauthorizedHandler(null)
})

it('prefills the editable reply from the AI draft and enables all actions in DRAFT_READY', async () => {
  renderDetail(message())
  expect(await screen.findByRole('textbox')).toHaveValue('Hello Ana, we will check.')
  expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  expect(screen.getByRole('button', { name: 'Approve' })).toBeEnabled()
  expect(screen.getByRole('button', { name: 'Regenerate' })).toBeEnabled()
})

it('offers only Regenerate and an explanation in AI_FAILED', async () => {
  renderDetail(message({ status: 'AI_FAILED', category: null, aiDraft: null, aiErrorCode: 'TIMEOUT' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('took too long')
  expect(screen.getByRole('button', { name: 'Regenerate' })).toBeEnabled()
  expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
})

it('is read-only when APPROVED', async () => {
  renderDetail(message({ status: 'APPROVED', finalReply: 'Final text', approvedAt: '2026-11-03T10:00:00Z' }))
  expect(await screen.findByTestId('approved-reply')).toHaveTextContent('Final text')
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
})

it('offers regenerate for a message stuck in processing', async () => {
  const user = renderDetail(message({ status: 'PENDING_AI', aiDraft: null, category: null, canRegenerate: true }))
  expect(await screen.findByText(/seems to be stuck/)).toBeInTheDocument()
  expect(screen.queryByText(/still processing/)).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()

  fetchMock.mockResolvedValueOnce(json(message({ version: 1 })))
  await user.click(screen.getByRole('button', { name: 'Regenerate' }))

  expect(await screen.findByTestId('ai-draft')).toHaveTextContent('Hello Ana, we will check.')
  expect(lastRequest()).toMatchObject({ url: expect.stringContaining('/api/messages/m1/regenerate'), method: 'POST' })
})

it('shows no actions while the AI is still processing', async () => {
  renderDetail(message({ status: 'PENDING_AI', aiDraft: null, category: null }))
  expect(await screen.findByText(/still processing/)).toBeInTheDocument()
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})

it('saves the edited reply with the current version and adopts the response', async () => {
  const user = renderDetail(message())
  const box = await screen.findByRole('textbox')
  await user.clear(box)
  await user.type(box, 'My edit')
  fetchMock.mockResolvedValueOnce(json(message({ finalReply: 'My edit', version: 1 })))
  await user.click(screen.getByRole('button', { name: 'Save' }))

  await screen.findByDisplayValue('My edit')
  expect(lastRequest()).toEqual({
    url: expect.stringContaining('/api/messages/m1/reply'),
    method: 'PUT',
    body: { finalReply: 'My edit', version: 0 },
  })
  expect(screen.getByTestId('ai-draft')).toHaveTextContent('Hello Ana, we will check.')
})

it('approves and becomes read-only', async () => {
  const user = renderDetail(message())
  await screen.findByRole('textbox')
  fetchMock.mockResolvedValueOnce(
    json(message({ status: 'APPROVED', finalReply: 'Hello Ana, we will check.', version: 1 })),
  )
  await user.click(screen.getByRole('button', { name: 'Approve' }))

  expect(await screen.findByTestId('approved-reply')).toBeInTheDocument()
  expect(lastRequest()).toMatchObject({ method: 'POST', body: { version: 0 } })
  expect(lastRequest().url).toContain('/approve')
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})

it('shows a reload prompt on 409 and reloads the message', async () => {
  const user = renderDetail(message())
  await screen.findByRole('textbox')
  fetchMock.mockResolvedValueOnce(json({ title: 'Conflict' }, 409))
  await user.click(screen.getByRole('button', { name: 'Save' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('changed elsewhere')
  expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()

  fetchMock.mockResolvedValueOnce(json(message({ aiDraft: 'Newer draft', version: 3 })))
  await user.click(screen.getByRole('button', { name: 'Reload' }))
  expect(await screen.findByDisplayValue('Newer draft')).toBeInTheDocument()
  expect(screen.queryByText(/changed elsewhere/)).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
})

it('regenerates from AI_FAILED and shows the new draft', async () => {
  const user = renderDetail(message({ status: 'AI_FAILED', category: null, aiDraft: null, aiErrorCode: 'PROVIDER_ERROR' }))
  await screen.findByRole('alert')
  fetchMock.mockResolvedValueOnce(json(message({ aiDraft: 'Fresh draft', version: 1 })))
  await user.click(screen.getByRole('button', { name: 'Regenerate' }))

  expect(await screen.findByDisplayValue('Fresh draft')).toBeInTheDocument()
  expect(lastRequest()).toMatchObject({ method: 'POST' })
  expect(lastRequest().url).toContain('/regenerate')
})

it('asks for confirmation before regenerating over unsaved edits', async () => {
  const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
  const user = renderDetail(message())
  await user.type(await screen.findByRole('textbox'), ' extra')
  await user.click(screen.getByRole('button', { name: 'Regenerate' }))

  expect(confirmSpy).toHaveBeenCalled()
  expect(fetchMock).toHaveBeenCalledTimes(1)
  confirmSpy.mockRestore()
})

it('renders HTML in AI and customer text as plain text', async () => {
  renderDetail(message({ content: '<b>bold</b>', aiDraft: '<img src=x onerror=alert(1)>' }))
  expect(await screen.findByText('<b>bold</b>')).toBeInTheDocument()
  expect(screen.getByTestId('ai-draft')).toHaveTextContent('<img src=x onerror=alert(1)>')
  expect(document.querySelector('img, b')).toBeNull()
})

it('shows a not-found message for an unknown id', async () => {
  fetchMock.mockResolvedValueOnce(json({ title: 'Not Found' }, 404))
  vi.stubGlobal('fetch', fetchMock)
  render(
    <MemoryRouter initialEntries={['/messages/nope']}>
      <Routes>
        <Route path="/messages/:id" element={<MessageDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
  expect(await screen.findByRole('alert')).toHaveTextContent('Message not found.')
})

it('links to the customer history only when an email is known', async () => {
  renderDetail(message({ customerEmail: 'ana@example.com' }))
  const link = await screen.findByRole('link', { name: 'Other messages from this customer' })
  expect(link).toHaveAttribute('href', '/?customerEmail=ana%40example.com')
})

it('shows no customer history link without an email', async () => {
  renderDetail(message())
  await screen.findByText('Where is my order?')
  expect(screen.queryByRole('link', { name: 'Other messages from this customer' })).not.toBeInTheDocument()
})
