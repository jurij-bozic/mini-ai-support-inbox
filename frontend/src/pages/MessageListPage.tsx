import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  CATEGORIES,
  listMessages,
  MESSAGE_STATUSES,
  type Category,
  type MessageStatus,
  type MessageSummary,
  type PageResponse,
} from '../api/messages'
import { CategoryBadge, StatusBadge } from '../components/Badges'
import { categoryLabel, statusLabel } from '../components/labels'

function parseEnum<T extends string>(value: string | null, allowed: readonly T[]): T | undefined {
  return allowed.find((candidate) => candidate === value)
}

function parsePage(value: string | null): number {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 0
}

function customerLabel(message: MessageSummary): string {
  return message.customerName || message.customerEmail || 'Anonymous'
}

export function MessageListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = parseEnum<MessageStatus>(searchParams.get('status'), MESSAGE_STATUSES)
  const category = parseEnum<Category>(searchParams.get('category'), CATEGORIES)
  const page = parsePage(searchParams.get('page'))
  const [reloadCount, setReloadCount] = useState(0)

  const requestKey = `${status ?? ''}|${category ?? ''}|${page}|${reloadCount}`
  const [result, setResult] = useState<{ key: string; data?: PageResponse<MessageSummary>; error?: string } | null>(
    null,
  )
  const [slowKey, setSlowKey] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    listMessages({ status, category, page }, { signal: controller.signal, onSlow: () => setSlowKey(requestKey) })
      .then((data) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, data })
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return
        setResult({ key: requestKey, error: err instanceof Error ? err.message : 'Something went wrong.' })
      })
    return () => controller.abort()
  }, [status, category, page, requestKey])

  const current = result?.key === requestKey ? result : null
  const loading = current === null
  const slow = slowKey === requestKey
  const data = current?.data ?? null
  const error = current?.error ?? null

  const updateParams = useCallback(
    (changes: { status?: string; category?: string; page?: number }) => {
      const next = new URLSearchParams(searchParams)
      for (const [key, value] of Object.entries(changes)) {
        if (value === undefined || value === '' || value === 0) next.delete(key)
        else next.set(key, String(value))
      }
      setSearchParams(next)
    },
    [searchParams, setSearchParams],
  )

  const filtersActive = status !== undefined || category !== undefined
  const totalPages = data?.totalPages ?? 0

  return (
    <section>
      <h2>Messages</h2>

      <div className="filters">
        <label>
          Status{' '}
          <select value={status ?? ''} onChange={(e) => updateParams({ status: e.target.value, page: 0 })}>
            <option value="">All</option>
            {MESSAGE_STATUSES.map((value) => (
              <option key={value} value={value}>
                {statusLabel(value)}
              </option>
            ))}
          </select>
        </label>{' '}
        <label>
          Category{' '}
          <select value={category ?? ''} onChange={(e) => updateParams({ category: e.target.value, page: 0 })}>
            <option value="">All</option>
            {CATEGORIES.map((value) => (
              <option key={value} value={value}>
                {categoryLabel(value)}
              </option>
            ))}
          </select>
        </label>
      </div>

      {loading && (
        <p role="status">{slow ? 'The server is waking up, this can take up to a minute…' : 'Loading messages…'}</p>
      )}

      {error && (
        <div role="alert">
          <p>Could not load messages: {error}</p>
          <button onClick={() => setReloadCount((count) => count + 1)}>Retry</button>
        </div>
      )}

      {!loading && !error && data && data.items.length === 0 && (
        <p>{filtersActive ? 'No messages match the selected filters.' : 'No messages yet.'}</p>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <table>
            <thead>
              <tr>
                <th>Created</th>
                <th>Customer</th>
                <th>Message</th>
                <th>Category</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((message) => (
                <tr key={message.id}>
                  <td>{new Date(message.createdAt).toLocaleString()}</td>
                  <td>{customerLabel(message)}</td>
                  <td>
                    <Link to={`/messages/${message.id}`}>{message.contentPreview}</Link>
                  </td>
                  <td>
                    <CategoryBadge category={message.category} />
                  </td>
                  <td>
                    <StatusBadge status={message.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <nav aria-label="Pagination">
            <button disabled={data.page <= 0} onClick={() => updateParams({ page: data.page - 1 })}>
              Previous
            </button>{' '}
            <span>
              Page {data.page + 1} of {totalPages} ({data.totalElements} messages)
            </span>{' '}
            <button disabled={data.page + 1 >= totalPages} onClick={() => updateParams({ page: data.page + 1 })}>
              Next
            </button>
          </nav>
        </>
      )}
    </section>
  )
}
