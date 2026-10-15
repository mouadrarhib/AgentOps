import { useState, type FormEvent } from 'react'
import { Button } from '../../../components/motion/button/base'
import { humanize, ticketCategories, ticketPriorities, ticketStatuses } from '../../../lib/api'
import type { TicketFilters } from '../types'

export function TicketFiltersBar({ filters, onChange, staff }: {
  filters: TicketFilters
  onChange: (filters: TicketFilters) => void
  staff: boolean
}) {
  const [orderId, setOrderId] = useState('')
  const [customerId, setCustomerId] = useState('')
  const [assignedToId, setAssignedToId] = useState('')
  const [error, setError] = useState('')

  function applyIds(event: FormEvent) {
    event.preventDefault()
    const values = [orderId, customerId, assignedToId]
    if (values.some(value => value.trim() && (!Number.isSafeInteger(Number(value)) || Number(value) <= 0))) {
      setError('IDs must be positive whole numbers.')
      return
    }
    setError('')
    onChange({
      ...filters,
      orderId: orderId.trim() ? Number(orderId) : undefined,
      customerId: customerId.trim() ? Number(customerId) : undefined,
      assignedToId: assignedToId.trim() ? Number(assignedToId) : undefined,
    })
  }

  return <>
    <div className="filter-bar">
      <label>Status<select value={filters.status || ''} onChange={event => onChange({ ...filters, status: (event.target.value || undefined) as TicketFilters['status'] })}><option value="">All statuses</option>{ticketStatuses.map(value => <option key={value} value={value}>{humanize(value)}</option>)}</select></label>
      <label>Category<select value={filters.category || ''} onChange={event => onChange({ ...filters, category: (event.target.value || undefined) as TicketFilters['category'] })}><option value="">All categories</option>{ticketCategories.map(value => <option key={value} value={value}>{humanize(value)}</option>)}</select></label>
      <label>Priority<select value={filters.priority || ''} onChange={event => onChange({ ...filters, priority: (event.target.value || undefined) as TicketFilters['priority'] })}><option value="">All priorities</option>{ticketPriorities.map(value => <option key={value} value={value}>{humanize(value)}</option>)}</select></label>
    </div>
    {staff && <form className="filter-bar filter-id-form" onSubmit={applyIds} noValidate>
      <label>Order ID<input inputMode="numeric" value={orderId} onChange={event => setOrderId(event.target.value)} placeholder="Any order"/></label>
      <label>Customer ID<input inputMode="numeric" value={customerId} onChange={event => setCustomerId(event.target.value)} placeholder="Any customer"/></label>
      <label>Assignee ID<input inputMode="numeric" value={assignedToId} disabled={filters.unassigned} onChange={event => setAssignedToId(event.target.value)} placeholder="Any assignee"/></label>
      <label className="filter-check"><input type="checkbox" checked={filters.unassigned || false} onChange={event => { setAssignedToId(''); setError(''); onChange({ ...filters, assignedToId: undefined, unassigned: event.target.checked || undefined }) }}/> Unassigned</label>
      <Button type="submit" variant="outline" size="sm">Apply IDs</Button>
      {error && <span className="filter-error" role="alert">{error}</span>}
    </form>}
  </>
}
