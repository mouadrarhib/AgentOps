import { useState } from 'react'
import { ArrowLeft, Plus } from '@phosphor-icons/react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { DataState, PageHeader } from '../../components/Workspace'
import { useAuth } from '../auth/AuthProvider'
import { CreateTicketForm } from './components/CreateTicketForm'
import { TicketDetails } from './components/TicketDetails'
import { TicketFiltersBar } from './components/TicketFilters'
import { TicketList } from './components/TicketList'
import { UpdateTicketForm } from './components/UpdateTicketForm'
import { useTicket, useTickets } from './hooks/use-tickets'
import type { TicketFilters } from './types'

const staffRole = (role?: string) => role === 'SUPPORT' || role === 'ADMIN'

export function TicketsPage() {
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<TicketFilters>({})
  const { user } = useAuth()
  const staff = staffRole(user?.role)
  const query = useTickets(page, filters)
  function changeFilters(next: TicketFilters) { setFilters(next); setPage(0) }
  return <div className="workspace-page"><PageHeader title="Support tickets" description="Track requests and find the order behind each ticket." action={<Link className="primary-link" to="/tickets/new"><Plus size={17}/> New ticket</Link>}/><TicketFiltersBar filters={filters} onChange={changeFilters} staff={staff}/><DataState loading={query.isPending} error={query.error} onRetry={() => void query.refetch()} empty={false}>{query.data && <TicketList tickets={query.data.content} total={query.data.totalElements} page={page} totalPages={query.data.totalPages} onPageChange={setPage} filtered={Object.values(filters).some(value => value !== undefined && value !== '')}/>}</DataState></div>
}

export function NewTicketPage() {
  return <div className="workspace-page"><Link className="back-link" to="/tickets"><ArrowLeft size={17}/> All tickets</Link><PageHeader title="Create a ticket" description="Open a support request for an order you are authorized to access."/><CreateTicketForm/></div>
}

export function TicketDetailPage() {
  const id = Number(useParams().ticketId)
  const location = useLocation()
  const { user } = useAuth()
  const query = useTicket(id)
  if (!Number.isInteger(id) || id <= 0) return <div className="data-state data-error" role="alert">Invalid ticket ID.</div>
  return <div className="workspace-page"><Link className="back-link" to="/tickets"><ArrowLeft size={17}/> All tickets</Link><PageHeader title={`Ticket #${id}`} description="Support request and its current state."/>{(location.state as { created?: boolean } | null)?.created && <p className="success-message" role="status">Ticket created successfully.</p>}<DataState loading={query.isPending} error={query.error} onRetry={() => void query.refetch()} empty={false}>{query.data && <><TicketDetails ticket={query.data}/>{staffRole(user?.role) && <UpdateTicketForm id={id}/>}</>}</DataState></div>
}
