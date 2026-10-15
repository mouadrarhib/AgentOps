import { api } from '../../../lib/api'
import type { TicketFilters, TicketUpdate } from '../types'

export const ticketsApi = {
  list: (page: number, size: number, filters: TicketFilters) => api.tickets(page, size, filters),
  get: (id: number) => api.ticket(id),
  create: api.createTicket,
  update: (id: number, body: TicketUpdate) => api.updateTicket(id, body),
}
