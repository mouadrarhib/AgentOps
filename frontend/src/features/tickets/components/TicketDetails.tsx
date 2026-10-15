import { Link } from 'react-router-dom'
import { humanize, type Ticket } from '../../../lib/api'
import { StatusBadge } from '../../../components/Workspace'

export function TicketDetails({ ticket }: { ticket: Ticket }) {
  return <section className="content-panel detail-panel"><div className="panel-heading"><h2>Ticket information</h2><StatusBadge value={ticket.status}/></div><dl className="detail-grid"><div><dt>Category</dt><dd>{humanize(ticket.category)}</dd></div><div><dt>Order</dt><dd><Link to={`/orders/${ticket.orderId}`}>#{ticket.orderId}</Link></dd></div><div><dt>Priority</dt><dd><StatusBadge value={ticket.priority}/></dd></div><div><dt>Customer ID</dt><dd>#{ticket.customerId}</dd></div><div><dt>Assigned staff ID</dt><dd>{ticket.assignedToId ? `#${ticket.assignedToId}` : 'Unassigned'}</dd></div></dl></section>
}
