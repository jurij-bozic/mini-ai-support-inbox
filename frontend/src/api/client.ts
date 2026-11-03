import { ApiError, NetworkError, TimeoutError, type FieldErrors } from './errors'

export interface Credentials {
  username: string
  password: string
}

export interface RequestOptions {
  method?: string
  body?: unknown
  /** Overrides the stored credentials (used to verify a login attempt). */
  credentials?: Credentials
  /** Called once if the request is still pending after SLOW_AFTER_MS (backend cold start). */
  onSlow?: () => void
  /** Set to false so a 401 does not trigger the global unauthorized handler. */
  handleUnauthorized?: boolean
  /** Lets the caller cancel the request (e.g. when a component unmounts). */
  signal?: AbortSignal
}

export const REQUEST_TIMEOUT_MS = 60_000
export const SLOW_AFTER_MS = 4_000

const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

let storedCredentials: Credentials | null = null
let unauthorizedHandler: (() => void) | null = null

export function setCredentials(credentials: Credentials | null) {
  storedCredentials = credentials
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

/** UTF-8 safe Base64, since btoa alone only accepts Latin-1. */
function basicAuthHeader({ username, password }: Credentials): string {
  const bytes = new TextEncoder().encode(`${username}:${password}`)
  const binary = Array.from(bytes, (b) => String.fromCharCode(b)).join('')
  return `Basic ${btoa(binary)}`
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: { title?: unknown; detail?: unknown; errors?: unknown } = {}
  try {
    problem = await response.json()
  } catch {
    // Not JSON (e.g. proxy error page): fall back to generic values below.
  }
  const title = typeof problem.title === 'string' ? problem.title : response.statusText || 'Request failed'
  const detail = typeof problem.detail === 'string' ? problem.detail : undefined
  return new ApiError(response.status, title, detail, parseFieldErrors(problem.errors))
}

/**
 * The backend sends validation errors as `[{ "field": "content", "message": "..." }]`.
 * If a field has several violations, the first one wins.
 */
function parseFieldErrors(errors: unknown): FieldErrors {
  if (!Array.isArray(errors)) return {}
  const result: FieldErrors = {}
  for (const entry of errors) {
    if (typeof entry !== 'object' || entry === null) continue
    const { field, message } = entry as { field?: unknown; message?: unknown }
    if (typeof field === 'string' && typeof message === 'string' && !(field in result)) result[field] = message
  }
  return result
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, onSlow, handleUnauthorized = true } = options
  const credentials = options.credentials ?? storedCredentials

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (credentials) headers.Authorization = basicAuthHeader(credentials)
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const controller = new AbortController()
  if (options.signal?.aborted) controller.abort()
  options.signal?.addEventListener('abort', () => controller.abort(), { once: true })
  let timedOut = false
  const timeout = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, REQUEST_TIMEOUT_MS)
  const slowTimer = onSlow ? setTimeout(onSlow, SLOW_AFTER_MS) : undefined

  try {
    let response: Response
    try {
      response = await fetch(`${baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      })
    } catch {
      throw timedOut ? new TimeoutError() : new NetworkError()
    }

    if (!response.ok) {
      if (response.status === 401 && handleUnauthorized) unauthorizedHandler?.()
      throw await toApiError(response)
    }
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
  } finally {
    clearTimeout(timeout)
    clearTimeout(slowTimer)
  }
}
