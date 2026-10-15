import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '../../../components/motion/button/base'
import { humanize, ticketCategories } from '../../../lib/api'
import { ApiError } from '../../../lib/http'
import { useCreateTicket } from '../hooks/use-tickets'
import type { TicketCategory } from '../types'

export function CreateTicketForm() {
  const [search] = useSearchParams()
  const navigate = useNavigate()
  const [orderId, setOrderId] = useState(search.get('orderId') || '')
  const [category, setCategory] = useState<TicketCategory>('ORDER')
  const [validation, setValidation] = useState('')
  const mutation = useCreateTicket()
  const id = Number(orderId)
  const validId = Number.isSafeInteger(id) && id > 0
  function submit(event: FormEvent) {
    event.preventDefault(); setValidation('')
    if (!validId) { setValidation('Enter a positive whole order ID.'); return }
    mutation.mutate({ orderId: id, category }, { onSuccess: ticket => navigate(`/tickets/${ticket.id}`, { replace: true, state: { created: true } }) })
  }
  const fieldErrors = mutation.error instanceof ApiError ? mutation.error.fields : []
  return <section className="content-panel form-panel"><form onSubmit={submit} noValidate><label htmlFor="ticket-order">Order ID</label><input id="ticket-order" type="number" min="1" step="1" required aria-invalid={!validId && orderId.length > 0} value={orderId} onChange={event => setOrderId(event.target.value)} placeholder="Enter an order ID"/><p className="field-hint">Choose an order from the <Link to="/orders">Orders</Link> page. Customers can create tickets only for their own orders.</p>{fieldErrors.filter(error => error.field === 'orderId').map((error, index) => <span className="field-error" key={index}>{error.message}</span>)}<label htmlFor="ticket-category">Category</label><select id="ticket-category" required value={category} onChange={event => setCategory(event.target.value as TicketCategory)}>{ticketCategories.map(value => <option key={value} value={value}>{humanize(value)}</option>)}</select>{fieldErrors.filter(error => error.field === 'category').map((error, index) => <span className="field-error" key={index}>{error.message}</span>)}{(validation || mutation.error) && <div className="form-error" role="alert">{validation || mutation.error?.message}{fieldErrors.length > 0 && <ul>{fieldErrors.map((error, index) => <li key={index}>{error.field}: {error.message}</li>)}</ul>}</div>}{mutation.isSuccess && <p className="success-message" role="status">Ticket created.</p>}<Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Creating ticket…' : 'Create ticket'}</Button></form></section>
}
