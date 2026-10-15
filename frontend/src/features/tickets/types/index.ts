import type { Ticket, TicketCategory, TicketPriority, TicketStatus } from '../../../lib/api'

export type { Ticket, TicketCategory, TicketPriority, TicketStatus }
export type TicketUpdate = { status?: TicketStatus; priority?: TicketPriority; assignedToId?: number; unassign?: boolean }
export type TicketFilters = { status?: TicketStatus; priority?: TicketPriority; category?: TicketCategory; orderId?: number; customerId?: number; assignedToId?: number; unassigned?: boolean }
