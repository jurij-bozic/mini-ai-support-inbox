import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, REQUEST_TIMEOUT_MS, setCredentials, setUnauthorizedHandler, SLOW_AFTER_MS } from './client'
import { ApiError, NetworkError, TimeoutError } from './errors'

const fetchMock = vi.fn()

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  setCredentials(null)
  setUnauthorizedHandler(null)
})

afterEach(() => {
  fetchMock.mockReset()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('apiFetch', () => {
  it('sends the Basic header, JSON body and parses the response', async () => {
    setCredentials({ username: 'admin', password: 'pässword' })
    fetchMock.mockResolvedValue(json({ ok: true }))

    const result = await apiFetch<{ ok: boolean }>('/api/x', { method: 'POST', body: { a: 1 } })

    expect(result).toEqual({ ok: true })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toContain('/api/x')
    expect(init.method).toBe('POST')
    expect(init.body).toBe('{"a":1}')
    expect(init.headers['Content-Type']).toBe('application/json')
    const expected = btoa(String.fromCharCode(...new TextEncoder().encode('admin:pässword')))
    expect(init.headers.Authorization).toBe(`Basic ${expected}`)
  })

  it('sends no Authorization header without credentials', async () => {
    fetchMock.mockResolvedValue(json({}))
    await apiFetch('/api/x')
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('parses ProblemDetail errors including field errors', async () => {
    fetchMock.mockResolvedValue(
      json({ title: 'Validation failed', detail: 'Bad input', errors: [{ field: 'content', message: 'must not be blank' }] }, 400),
    )
    const error = (await apiFetch('/api/x').catch((e) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.title).toBe('Validation failed')
    expect(error.message).toBe('Bad input')
    expect(error.fieldErrors).toEqual({ content: 'must not be blank' })
  })

  it('keeps the first message per field and ignores malformed field error entries', async () => {
    fetchMock.mockResolvedValue(
      json(
        {
          title: 'Validation failed',
          errors: [
            { field: 'content', message: 'first' },
            { field: 'content', message: 'second' },
            { field: 'customerEmail', message: 'bad email' },
            { field: 42, message: 'wrong type' },
            'not an object',
            null,
          ],
        },
        400,
      ),
    )
    const error = (await apiFetch('/api/x').catch((e) => e)) as ApiError
    expect(error.fieldErrors).toEqual({ content: 'first', customerEmail: 'bad email' })
  })

  it('returns no field errors when errors is not an array', async () => {
    fetchMock.mockResolvedValue(json({ title: 'Validation failed', errors: { content: 'object form' } }, 400))
    const error = (await apiFetch('/api/x').catch((e) => e)) as ApiError
    expect(error.fieldErrors).toEqual({})
  })

  it('falls back to a generic error for non-JSON error bodies', async () => {
    fetchMock.mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }))
    const error = (await apiFetch('/api/x').catch((e) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(502)
    expect(error.title).toBe('Bad Gateway')
  })

  it('calls the unauthorized handler on 401 unless disabled', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    fetchMock.mockImplementation(() => Promise.resolve(json({ title: 'Unauthorized' }, 401)))

    await expect(apiFetch('/api/x')).rejects.toBeInstanceOf(ApiError)
    expect(handler).toHaveBeenCalledOnce()

    await expect(apiFetch('/api/x', { handleUnauthorized: false })).rejects.toBeInstanceOf(ApiError)
    expect(handler).toHaveBeenCalledOnce()
  })

  it('maps fetch failures to NetworkError', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(apiFetch('/api/x')).rejects.toBeInstanceOf(NetworkError)
  })

  it('aborts after the timeout and throws TimeoutError', async () => {
    vi.useFakeTimers()
    fetchMock.mockImplementation(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal!.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
        }),
    )
    const promise = apiFetch('/api/x').catch((e) => e)
    await vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS)
    expect(await promise).toBeInstanceOf(TimeoutError)
  })

  it('fires onSlow only when the request is slow', async () => {
    vi.useFakeTimers()
    const onSlow = vi.fn()
    let resolve!: (r: Response) => void
    fetchMock.mockReturnValue(new Promise<Response>((r) => (resolve = r)))

    const promise = apiFetch('/api/x', { onSlow })
    await vi.advanceTimersByTimeAsync(SLOW_AFTER_MS)
    expect(onSlow).toHaveBeenCalledOnce()
    resolve(json({}))
    await promise
  })
})
