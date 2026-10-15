import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ticketsApi } from '../api/tickets-api'
import type { TicketFilters, TicketUpdate } from '../types'

export function useTickets(page: number, filters: TicketFilters) {
  return useQuery({ queryKey: ['tickets', page, 10, filters], queryFn: () => ticketsApi.list(page, 10, filters) })
}

export function useTicket(id: number) {
  return useQuery({ queryKey: ['ticket', id], queryFn: () => ticketsApi.get(id), enabled: Number.isInteger(id) && id > 0 })
}

export function useCreateTicket() {
  const client = useQueryClient()
  return useMutation({ mutationFn: ticketsApi.create, onSuccess: () => client.invalidateQueries({ queryKey: ['tickets'] }) })
}

export function useUpdateTicket(id: number) {
  const client = useQueryClient()
  return useMutation({ mutationFn: (body: TicketUpdate) => ticketsApi.update(id, body), onSuccess: () => { void client.invalidateQueries({ queryKey: ['ticket', id] }); void client.invalidateQueries({ queryKey: ['tickets'] }) } })
}
