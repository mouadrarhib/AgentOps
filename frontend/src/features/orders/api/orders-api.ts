import { api } from '../../../lib/api'

export const ordersApi = {
  list: (page: number, size: number) => api.orders(page, size),
  get: (id: number) => api.order(id),
}
