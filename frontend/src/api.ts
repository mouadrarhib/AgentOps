export type User = { id: number; email: string; role: 'CUSTOMER' | 'SUPPORT' | 'ADMIN' }
export type Auth = { accessToken: string; refreshToken: string; user: User }
export type Page<T> = { content: T[]; page: number; totalElements: number; totalPages: number }
export type Order = { id: number; customerId: number; status: string; createdAt: string }
export type Ticket = { id: number; orderId: number; customerId: number; category: string; status: string; priority: string; assignedToId: number | null }
export type Conversation = { id: number; createdAt: string }
export type Message = { id: number; role: 'USER' | 'ASSISTANT'; content: string; createdAt: string }
export type ConversationDetail = { conversation: Conversation; messages: Message[]; totalPages: number }
export type ChatResponse = { conversationId: number; reply: Message; toolCalls: number }

const base = (import.meta.env.VITE_API_BASE_URL || '/').replace(/\/$/, '')
let accessToken: string | null = null
let refreshToken: string | null = null
export function setTokens(auth: Auth | null) {
  accessToken = auth?.accessToken ?? null
  refreshToken = auth?.refreshToken ?? null
}

async function send<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  const response = await fetch(`${base}${path}`, { ...init, headers, credentials: 'omit' })
  if (response.status === 401 && retry && refreshToken && path !== '/api/auth/refresh') {
    try {
      const auth = await send<Auth>('/api/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken }) }, false)
      setTokens(auth)
      return send<T>(path, init, false)
    } catch { setTokens(null) }
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as { detail?: string; title?: string }
    throw new Error(error.detail || error.title || `Request failed (${response.status})`)
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}
export const api = {
  login: (email: string, password: string) => send<Auth>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  register: (email: string, password: string) => send<Auth>('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }),
  me: () => send<User>('/api/auth/me'),
  logout: () => send<void>('/api/auth/logout', { method: 'POST' }),
  orders: (page = 0) => send<Page<Order>>(`/orders?page=${page}&size=20`),
  tickets: (page = 0, status = '') => send<Page<Ticket>>(`/tickets?page=${page}&size=20${status ? `&status=${status}` : ''}`),
  createTicket: (orderId: number, category: string) => send<Ticket>('/tickets', { method: 'POST', body: JSON.stringify({ orderId, category }) }),
  updateTicket: (id: number, update: { status?: string; priority?: string }) => send<Ticket>(`/tickets/${id}`, { method: 'PATCH', body: JSON.stringify(update) }),
  conversations: () => send<Page<Conversation>>('/agent/conversations?page=0&size=100'),
  conversation: (id: number) => send<ConversationDetail>(`/agent/conversations/${id}?page=0&size=100`),
  chat: (message: string, conversationId?: number) => send<ChatResponse>('/agent/chat', { method: 'POST', body: JSON.stringify({ message, conversationId }) }),
}
