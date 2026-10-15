import { useQuery } from '@tanstack/react-query'
import { ordersApi } from '../api/orders-api'

export function useOrders(page: number) {
  return useQuery({ queryKey: ['orders', page, 10], queryFn: () => ordersApi.list(page, 10) })
}

export function useOrder(id: number, enabled = true) {
  return useQuery({ queryKey: ['order', id], queryFn: () => ordersApi.get(id), enabled: enabled && Number.isInteger(id) && id > 0 })
}
