import { request } from './http'

export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Order = {
  id: number
  customerId: number
  status: 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REFUNDED'
  createdAt: string
  updatedAt: string
}

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type TicketPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type TicketCategory = 'ORDER' | 'PAYMENT' | 'DELIVERY' | 'RETURN' | 'OTHER'
export type Ticket = {
  id: number
  orderId: number
  customerId: number
  category: TicketCategory
  status: TicketStatus
  priority: TicketPriority
  assignedToId: number | null
}

export type OrderStatus = Order['status']

export type Conversation = { id: number; userId: number; createdAt: string }
export type Message = {
  id: number
  conversationId: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}
export type ConversationDetail = {
  conversation: Conversation
  messages: Message[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
export type AgentReply = { conversationId: number; reply: Message; toolCalls: number }

export const ticketStatuses: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
export const ticketPriorities: TicketPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT']
export const ticketCategories: TicketCategory[] = ['ORDER', 'PAYMENT', 'DELIVERY', 'RETURN', 'OTHER']

const params = (values: Record<string, string | number | boolean | undefined>) => {
  const query = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => { if (value !== undefined && value !== '') query.set(key, String(value)) })
  return query.toString()
}

export const api = {
  orders: (page = 0, size = 20) => request<Page<Order>>(`/orders?${params({ page, size })}`),
  order: (id: number) => request<Order>(`/orders/${id}`),
  tickets: (page = 0, size = 20, filters: { status?: TicketStatus; priority?: TicketPriority; category?: TicketCategory; orderId?: number; customerId?: number; assignedToId?: number; unassigned?: boolean } = {}) =>
    request<Page<Ticket>>(`/tickets?${params({ page, size, ...filters })}`),
  ticket: (id: number) => request<Ticket>(`/tickets/${id}`),
  createTicket: (body: { orderId: number; category: TicketCategory }) =>
    request<Ticket>('/tickets', { method: 'POST', body: JSON.stringify(body) }),
  updateTicket: (id: number, body: { status?: TicketStatus; priority?: TicketPriority; assignedToId?: number; unassign?: boolean }) =>
    request<Ticket>(`/tickets/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  conversations: (page = 0, size = 30) => request<Page<Conversation>>(`/agent/conversations?${params({ page, size })}`),
  conversation: (id: number, page = 0, size = 100) =>
    request<ConversationDetail>(`/agent/conversations/${id}?${params({ page, size })}`),
  chat: (message: string, conversationId?: number) =>
    request<AgentReply>('/agent/chat', { method: 'POST', body: JSON.stringify({ message, ...(conversationId ? { conversationId } : {}) }) }),
}

export function dateLabel(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date)
}

export function timeLabel(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? '' : new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(date)
}

export function humanize(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase())
}
