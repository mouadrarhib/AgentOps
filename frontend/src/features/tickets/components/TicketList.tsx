import { ArrowUpRight, Ticket as TicketIcon } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { humanize, type Ticket } from '../../../lib/api'
import { Pager, StatusBadge } from '../../../components/Workspace'

export function TicketList({ tickets, total, page, totalPages, onPageChange, filtered }: {
  tickets: Ticket[]; total: number; page: number; totalPages: number; onPageChange: (page: number) => void; filtered: boolean
}) {
  if (!tickets.length) return <div className="data-state"><TicketIcon size={34} className="empty-icon"/><h2>{filtered ? 'No tickets match these filters' : 'No tickets found'}</h2><p>{filtered ? 'Adjust a filter to see more tickets.' : 'Create a ticket for an order when you need help.'}</p><Link to="/tickets/new" className="secondary-link">Create a ticket</Link></div>
  return <section className="content-panel table-panel"><div className="panel-heading"><h2>Tickets</h2><span>{total} total</span></div><div className="table-scroll"><table><thead><tr><th>Ticket</th><th>Order</th><th>Category</th><th>Status</th><th>Priority</th><th><span className="sr-only">Open</span></th></tr></thead><tbody>{tickets.map(ticket => <tr key={ticket.id}><td data-label="Ticket"><Link className="table-primary" to={`/tickets/${ticket.id}`}>#{ticket.id}</Link></td><td data-label="Order">#{ticket.orderId}</td><td data-label="Category">{humanize(ticket.category)}</td><td data-label="Status"><StatusBadge value={ticket.status}/></td><td data-label="Priority"><StatusBadge value={ticket.priority}/></td><td className="table-action"><Link className="row-arrow" aria-label={`Open ticket ${ticket.id}`} to={`/tickets/${ticket.id}`}><ArrowUpRight size={17}/></Link></td></tr>)}</tbody></table></div><Pager page={page} totalPages={totalPages} onChange={onPageChange}/></section>
}
