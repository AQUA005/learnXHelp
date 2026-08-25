/**
 * The one place the application talks to the server.
 *
 * Authentication is a session cookie, so every state-changing request has to
 * echo the CSRF token the server set. Errors arrive as RFC 7807 problem
 * details; they are unwrapped here so callers can rely on a single Error type.
 */

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** A failed request, carrying the status and any per-field messages. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }

  /** Whether the caller needs to sign in again. */
  get isUnauthenticated(): boolean {
    return this.status === 401
  }
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const entry = part.trim()
    if (entry.startsWith(prefix)) {
      return decodeURIComponent(entry.slice(prefix.length))
    }
  }
  return null
}

type ProblemDetail = {
  detail?: string
  message?: string
  title?: string
  errors?: Record<string, string>
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: ProblemDetail = {}
  try {
    body = (await response.json()) as ProblemDetail
  } catch {
    // A response without a JSON body, such as a gateway error.
  }
  const message =
    body.message ?? body.detail ?? body.title ?? `Request failed (${response.status})`
  return new ApiError(response.status, message, body.errors ?? {})
}

export type RequestOptions = {
  method?: string
  body?: unknown
  /** Sent as multipart instead of JSON. */
  formData?: FormData
  signal?: AbortSignal
}

/**
 * Performs a request and returns the parsed body.
 *
 * Throws {@link ApiError} for any non-2xx response.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers: Record<string, string> = {}

  if (!SAFE_METHODS.has(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  let payload: BodyInit | undefined
  if (options.formData) {
    // The browser sets the multipart boundary itself.
    payload = options.formData
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(options.body)
  }

  const response = await fetch(path, {
    method,
    headers,
    body: payload,
    signal: options.signal,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await toApiError(response)
  }

  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T
  }
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    return (await response.text()) as T
  }
  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, formData: FormData) => request<T>(path, { method: 'POST', formData }),
}

/**
 * Primes the CSRF cookie.
 *
 * The token is issued on any request, so this is called once at startup to
 * make sure it exists before the first sign-in attempt.
 */
export async function primeCsrfToken(): Promise<void> {
  try {
    await fetch('/api/auth/current-user', { credentials: 'same-origin' })
  } catch {
    // Offline at startup; the next request will try again.
  }
}
