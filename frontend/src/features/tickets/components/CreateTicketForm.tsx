import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '../../../components/motion/button/base'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { humanize, ticketCategories } from '../../../lib/api'
import { ApiError, errorMessage } from '../../../lib/http'
import { useCreateTicket } from '../hooks/use-tickets'
import type { TicketCategory } from '../types'

export function CreateTicketForm() {
  const [search] = useSearchParams()
  const navigate = useNavigate()
  const [orderId, setOrderId] = useState(search.get('orderId') || '')
  const [category, setCategory] = useState<TicketCategory>('ORDER')
  const [validation, setValidation] = useState('')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const mutation = useCreateTicket()
  const id = Number(orderId)
  const validId = /^\d+$/.test(orderId) && Number.isSafeInteger(id) && id > 0
  const fieldErrors = mutation.error instanceof ApiError ? mutation.error.fields : []

  function submit(event: FormEvent) {
    event.preventDefault()
    setValidation('')
    if (!validId) {
      setValidation('Enter a positive whole order ID.')
      return
    }
    mutation.reset()
    setConfirmOpen(true)
  }

  function createTicket() {
    mutation.mutate({ orderId: id, category }, {
      onSuccess: ticket => navigate(`/tickets/${ticket.id}`, { replace: true, state: { created: true } }),
      onError: () => setConfirmOpen(false),
    })
  }

  return <section className="content-panel form-panel">
    <form onSubmit={submit} noValidate>
      <label htmlFor="ticket-order">Order ID</label>
      <input id="ticket-order" type="number" min="1" step="1" required aria-invalid={!validId && orderId.length > 0} value={orderId} onChange={event => setOrderId(event.target.value)} placeholder="Enter an order ID"/>
      <p className="field-hint">Choose an order from the <Link to="/orders">Orders</Link> page. Customers can create tickets only for their own orders.</p>
      {fieldErrors.filter(field => field.field === 'orderId').map((field, index) => <span className="field-error" key={index}>{field.message}</span>)}
      <label htmlFor="ticket-category">Category</label>
      <select id="ticket-category" required value={category} onChange={event => setCategory(event.target.value as TicketCategory)}>{ticketCategories.map(value => <option key={value} value={value}>{humanize(value)}</option>)}</select>
      {fieldErrors.filter(field => field.field === 'category').map((field, index) => <span className="field-error" key={index}>{field.message}</span>)}
      {(validation || mutation.error) && <div className="form-error" role="alert">{validation || errorMessage(mutation.error)}{fieldErrors.length > 0 && <ul>{fieldErrors.map((field, index) => <li key={index}>{field.field}: {field.message}</li>)}</ul>}</div>}
      <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Creating ticket…' : 'Create ticket'}</Button>
    </form>
    <ConfirmDialog open={confirmOpen} title="Create support ticket?" description={`Open a ${humanize(category).toLowerCase()} ticket for order #${id}.`} confirmLabel="Create ticket" pending={mutation.isPending} onCancel={() => setConfirmOpen(false)} onConfirm={createTicket}/>
  </section>
}
