import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, ChatCircleDots, Package, Ticket } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { api, dateLabel } from '../../lib/api'
import { DataState, PageHeader, StatusBadge } from '../../components/Workspace'

export function OverviewPage() {
  const orders = useQuery({ queryKey: ['orders', 0, 5], queryFn: () => api.orders(0, 5) })
  const tickets = useQuery({ queryKey: ['tickets', 0, 5], queryFn: () => api.tickets(0, 5) })
  const loading = orders.isPending || tickets.isPending
  const error = orders.error || tickets.error
  const retry = () => { void orders.refetch(); void tickets.refetch() }

  return <div className="workspace-page">
    <PageHeader title="Overview" description="A clear view of your orders and support requests." />
    <div className="overview-hero"><div className="overview-hero-copy"><span className="hero-spark"><ChatCircleDots size={19} weight="fill" /> AI support</span><h2>What can I help you find?</h2><p>Ask about an order, check a ticket, or start a support request. AgentOps uses the records available to your account.</p><Link className="hero-link" to="/assistant">Open assistant <ArrowUpRight size={18} /></Link></div><div className="hero-orbit" aria-hidden="true"><span>AI</span><i /><i /><i /></div></div>
    <DataState loading={loading} error={error} onRetry={retry} empty={false}>
      <div className="stat-grid">
        <Link className="stat-tile" to="/orders"><span className="stat-icon"><Package size={21}/></span><span><strong>{orders.data?.totalElements ?? 0}</strong><small>Orders visible to you</small></span><ArrowUpRight size={18} /></Link>
        <Link className="stat-tile" to="/tickets"><span className="stat-icon violet"><Ticket size={21}/></span><span><strong>{tickets.data?.totalElements ?? 0}</strong><small>Support tickets</small></span><ArrowUpRight size={18} /></Link>
      </div>
      <div className="overview-columns">
        <section className="content-panel"><div className="panel-heading"><h2>Recent orders</h2><Link to="/orders">View all <ArrowUpRight size={16}/></Link></div>{orders.data?.content.length ? <div className="compact-list">{orders.data.content.map(order => <Link to={`/orders/${order.id}`} key={order.id} className="compact-row"><span><strong>Order #{order.id}</strong><small>{dateLabel(order.createdAt)}</small></span><StatusBadge value={order.status}/></Link>)}</div> : <p className="empty-copy">No orders are available to your account yet.</p>}</section>
        <section className="content-panel"><div className="panel-heading"><h2>Recent tickets</h2><Link to="/tickets">View all <ArrowUpRight size={16}/></Link></div>{tickets.data?.content.length ? <div className="compact-list">{tickets.data.content.map(ticket => <Link to={`/tickets/${ticket.id}`} key={ticket.id} className="compact-row"><span><strong>Ticket #{ticket.id}</strong><small>Order #{ticket.orderId} · {ticket.category.toLowerCase()}</small></span><StatusBadge value={ticket.status}/></Link>)}</div> : <p className="empty-copy">No tickets yet. You can create one for an authorized order.</p>}</section>
      </div>
    </DataState>
  </div>
}
