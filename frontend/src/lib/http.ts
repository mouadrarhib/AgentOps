export type FieldError = { field: string; message: string }

export class ApiError extends Error {
  constructor(public status: number, message: string, public fields: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
  }
}

const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const unsafe = new Set(['POST', 'PATCH', 'PUT', 'DELETE'])
let refreshInFlight: Promise<boolean> | undefined

async function errorFrom(response: Response): Promise<ApiError> {
  const body = await response.json().catch(() => ({})) as { detail?: string; title?: string; errors?: FieldError[] }
  return new ApiError(response.status, body.detail || body.title || `Request failed (${response.status}).`, body.errors || [])
}

async function csrfHeader(): Promise<Record<string, string>> {
  const response = await fetch(`${base}/api/auth/csrf`, { credentials: 'include' })
  if (!response.ok) throw await errorFrom(response)
  const token = await response.json() as { headerName: string; token: string }
  return { [token.headerName]: token.token }
}

async function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(`${base}/api/auth/refresh`, {
          method: 'POST', credentials: 'include', headers: await csrfHeader(),
        })
        return response.ok
      } catch { return false }
    })().finally(() => { refreshInFlight = undefined })
  }
  return refreshInFlight
}

export async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (unsafe.has(method)) {
    const csrf = await csrfHeader()
    for (const [name, value] of Object.entries(csrf)) headers.set(name, value)
  }
  const response = await fetch(`${base}${path}`, { ...init, method, headers, credentials: 'include' })
  if (response.status === 401 && retry && path !== '/api/auth/refresh' && path !== '/api/auth/login') {
    if (await refreshSession()) return request<T>(path, init, false)
  }
  if (!response.ok) throw await errorFrom(response)
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}
