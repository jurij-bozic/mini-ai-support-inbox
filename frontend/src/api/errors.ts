export type FieldErrors = Record<string, string>

/** A non-2xx response from the backend, parsed from an RFC 9457 ProblemDetail. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly fieldErrors: FieldErrors

  constructor(status: number, title: string, detail?: string, fieldErrors: FieldErrors = {}) {
    super(detail ?? title)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.fieldErrors = fieldErrors
  }
}

/** The request could not reach the backend (offline, DNS, CORS, ...). */
export class NetworkError extends Error {
  constructor() {
    super('Could not reach the server.')
    this.name = 'NetworkError'
  }
}

/** The request exceeded the client-side timeout. */
export class TimeoutError extends Error {
  constructor() {
    super('The request timed out.')
    this.name = 'TimeoutError'
  }
}
