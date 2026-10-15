import { ArrowUpRight, Package } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { dateLabel, type Order } from '../../../lib/api'
import { Pager, StatusBadge } from '../../../components/Workspace'

export function OrdersTable({ orders, total, page, totalPages, onPageChange, staff }: {
  orders: Order[]; total: number; page: number; totalPages: number; onPageChange: (page: number) => void; staff: boolean
}) {
  if (!orders.length) return <div className="data-state"><Package size={34} className="empty-icon"/><h2>No orders yet</h2><p>Orders you can access will appear here.</p></div>
  return <section className="content-panel table-panel"><div className="panel-heading"><h2>Orders</h2><span>{total} total</span></div><div className="table-scroll"><table><thead><tr><th>Order</th><th>Status</th>{staff && <th>Customer ID</th>}<th>Created</th><th><span className="sr-only">Open</span></th></tr></thead><tbody>{orders.map(order => <tr key={order.id}><td data-label="Order"><Link className="table-primary" to={`/orders/${order.id}`}>#{order.id}</Link></td><td data-label="Status"><StatusBadge value={order.status}/></td>{staff && <td data-label="Customer ID">#{order.customerId}</td>}<td data-label="Created">{dateLabel(order.createdAt)}</td><td className="table-action"><Link className="row-arrow" aria-label={`Open order ${order.id}`} to={`/orders/${order.id}`}><ArrowUpRight size={17}/></Link></td></tr>)}</tbody></table></div><Pager page={page} totalPages={totalPages} onChange={onPageChange}/></section>
}
