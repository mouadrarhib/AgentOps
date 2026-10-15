import { useState } from 'react'
import { ArrowLeft } from '@phosphor-icons/react'
import { Link, useParams } from 'react-router-dom'
import { DataState, PageHeader } from '../../components/Workspace'
import { useAuth } from '../auth/AuthProvider'
import { OrderDetails } from './components/OrderDetails'
import { OrdersTable } from './components/OrdersTable'
import { useOrder, useOrders } from './hooks/use-orders'

export function OrdersPage() {
  const [page, setPage] = useState(0)
  const query = useOrders(page)
  const role = useAuth().user?.role
  const staff = role === 'SUPPORT' || role === 'ADMIN'
  return <div className="workspace-page"><PageHeader title="Orders" description="Order records available to your account."/><DataState loading={query.isPending} error={query.error} onRetry={() => void query.refetch()} empty={false}>{query.data && <OrdersTable orders={query.data.content} total={query.data.totalElements} page={page} totalPages={query.data.totalPages} onPageChange={setPage} staff={staff}/>}</DataState></div>
}

export function OrderDetailPage() {
  const id = Number(useParams().orderId)
  const query = useOrder(id)
  const role = useAuth().user?.role
  const staff = role === 'SUPPORT' || role === 'ADMIN'
  if (!Number.isInteger(id) || id <= 0) return <div className="data-state data-error" role="alert">Invalid order ID.</div>
  return <div className="workspace-page"><Link className="back-link" to="/orders"><ArrowLeft size={17}/> All orders</Link><PageHeader title={`Order #${id}`} description="Order details available to your account."/><DataState loading={query.isPending} error={query.error} onRetry={() => void query.refetch()} empty={false}>{query.data && <OrderDetails order={query.data} staff={staff}/>}</DataState></div>
}
