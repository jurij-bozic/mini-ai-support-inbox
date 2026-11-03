import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import { setCredentials, setUnauthorizedHandler } from '../api/client'
import type { MessageSummary, PageResponse } from '../api/messages'
import { MessageListPage } from './MessageListPage'

const fetchMock = vi.fn()

function summary(overrides: Partial<MessageSummary> = {}): MessageSummary {
  return {
    id: 'id-1',
    customerName: 'Ana',
    customerEmail: null,
    contentPreview: 'Where is my order?',
    category: 'QUESTION',
    status: 'DRAFT_READY',
    createdAt: '2026-01-01T10:00:00Z',
    ...overrides,
  }
}

function pageOf(items: MessageSummary[], overrides: Partial<PageResponse<MessageSummary>> = {}) {
  return new Response(
    JSON.stringify({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1, ...overrides }),
    { status: 200 },
  )
}

function requestedUrl(call: number): URL {
  return new URL(String(fetchMock.mock.calls[call][0]), 'http://localhost')
}

function renderList(initialEntry = '/') {
  vi.stubGlobal('fetch', fetchMock)
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <MessageListPage />
    </MemoryRouter>,
  )
}

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
  setCredentials(null)
  setUnauthorizedHandler(null)
})

it('renders rows with badges and a link to the detail view', async () => {
  fetchMock.mockResolvedValue(pageOf([summary()]))
  renderList()

  const link = await screen.findByRole('link', { name: 'Where is my order?' })
  expect(link).toHaveAttribute('href', '/messages/id-1')
  expect(screen.getByText('Ana')).toBeInTheDocument()
  expect(screen.getByText('Question', { selector: '.badge' })).toBeInTheDocument()
  expect(screen.getByText('Draft ready', { selector: '.badge' })).toBeInTheDocument()
})

it('shows an unclassified badge when the category is missing', async () => {
  fetchMock.mockResolvedValue(pageOf([summary({ category: null, status: 'AI_FAILED' })]))
  renderList()
  expect(await screen.findByText('Unclassified')).toBeInTheDocument()
})

it('shows the empty state', async () => {
  fetchMock.mockResolvedValue(pageOf([], { totalPages: 0 }))
  renderList()
  expect(await screen.findByText('No messages yet.')).toBeInTheDocument()
})

it('shows an error and retries', async () => {
  fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ title: 'Boom' }), { status: 500 }))
  fetchMock.mockResolvedValueOnce(pageOf([summary()]))
  renderList()

  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load messages')
  await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
  expect(await screen.findByRole('link', { name: 'Where is my order?' })).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('sends filters to the backend and resets to the first page', async () => {
  fetchMock.mockImplementation(() => Promise.resolve(pageOf([summary()], { page: 0, totalPages: 3 })))
  renderList()
  await screen.findByRole('link', { name: 'Where is my order?' })

  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: 'Next' }))
  await waitFor(() => expect(requestedUrl(fetchMock.mock.calls.length - 1).searchParams.get('page')).toBe('1'))

  await user.selectOptions(screen.getByLabelText('Status'), 'APPROVED')
  await waitFor(() => {
    const url = requestedUrl(fetchMock.mock.calls.length - 1)
    expect(url.searchParams.get('status')).toBe('APPROVED')
    expect(url.searchParams.get('page')).toBe('0')
  })

  await user.selectOptions(screen.getByLabelText('Category'), 'PRAISE')
  await waitFor(() => {
    const url = requestedUrl(fetchMock.mock.calls.length - 1)
    expect(url.searchParams.get('category')).toBe('PRAISE')
    expect(url.searchParams.get('status')).toBe('APPROVED')
  })
})

it('disables Previous on the first page and Next on the last', async () => {
  fetchMock.mockResolvedValue(pageOf([summary()], { page: 0, totalPages: 1, totalElements: 1 }))
  renderList()
  await screen.findByText('Page 1 of 1 (1 messages)')
  expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
})

it('requests the next page', async () => {
  fetchMock.mockImplementation(() => Promise.resolve(pageOf([summary()], { page: 0, totalPages: 2, totalElements: 21 })))
  renderList()
  await screen.findByText('Page 1 of 2 (21 messages)')

  await userEvent.click(screen.getByRole('button', { name: 'Next' }))
  await waitFor(() => expect(requestedUrl(fetchMock.mock.calls.length - 1).searchParams.get('page')).toBe('1'))
})

it('filters by customer email from the URL and can clear the filter', async () => {
  fetchMock.mockResolvedValue(pageOf([summary({ customerEmail: 'ana@example.com' })]))
  renderList('/?customerEmail=ana%40example.com')

  expect(await screen.findByText(/Showing messages from ana@example.com/)).toBeInTheDocument()
  expect(requestedUrl(0).searchParams.get('customerEmail')).toBe('ana@example.com')

  await userEvent.click(screen.getByRole('button', { name: 'Clear' }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
  expect(requestedUrl(1).searchParams.has('customerEmail')).toBe(false)
  expect(screen.queryByText(/Showing messages from/)).not.toBeInTheDocument()
})
