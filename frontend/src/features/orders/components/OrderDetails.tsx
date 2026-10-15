import { Link } from 'react-router-dom'
import { dateLabel, type Order } from '../../../lib/api'
import { StatusBadge } from '../../../components/Workspace'

export function OrderDetails({ order, staff }: { order: Order; staff: boolean }) {
  return <section className="content-panel detail-panel"><div className="panel-heading"><h2>Order information</h2><StatusBadge value={order.status}/></div><dl className="detail-grid"><div><dt>Order ID</dt><dd>#{order.id}</dd></div>{staff && <div><dt>Customer ID</dt><dd>#{order.customerId}</dd></div>}<div><dt>Created</dt><dd>{dateLabel(order.createdAt)}</dd></div><div><dt>Updated</dt><dd>{dateLabel(order.updatedAt)}</dd></div></dl><Link className="secondary-link" to={`/tickets/new?orderId=${order.id}`}>Create support ticket</Link></section>
}
