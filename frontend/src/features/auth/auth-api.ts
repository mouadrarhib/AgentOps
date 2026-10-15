import { request } from '../../lib/http'

export type User = { id: number; email: string; role: 'CUSTOMER' | 'SUPPORT' | 'ADMIN' }
type AuthResponse = { user: User }

// The server also returns tokens in JSON. This browser client intentionally uses
// the HttpOnly cookie transport and never copies those tokens into JS storage.
export const authApi = {
  me: () => request<User>('/api/auth/me'),
  login: (email: string, password: string) => request<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }, false),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
}
