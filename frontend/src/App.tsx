import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { AuthProvider, useAuth } from './features/auth/AuthProvider'
import { AuthPage } from './features/auth/AuthPage'
import { AppShell } from './features/shell/AppShell'
import { OverviewPage } from './features/overview/OverviewPage'
import { OrdersPage, OrderDetailPage } from './features/orders/OrdersPage'
import { TicketsPage, NewTicketPage, TicketDetailPage } from './features/tickets/TicketsPage'
import { AssistantPage } from './features/agent/AssistantPage'

export function ProtectedRoute() {
  const { user, loading, error, retry } = useAuth()
  const location = useLocation()
  if (loading) return <div className="full-status" role="status">Checking your session…</div>
  if (error) return <div className="full-status error-state" role="alert"><strong>Could not check your session.</strong><p>{error.message}</p><button onClick={retry}>Try again</button></div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <AppShell />
}

export default function App() {
  const [queryClient] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } }))
  return <QueryClientProvider client={queryClient}><AuthProvider><BrowserRouter><Routes>
    <Route path="/login" element={<AuthPage/>}/>
    <Route element={<ProtectedRoute/>}>
      <Route path="/overview" element={<OverviewPage/>}/>
      <Route path="/orders" element={<OrdersPage/>}/>
      <Route path="/orders/:orderId" element={<OrderDetailPage/>}/>
      <Route path="/tickets" element={<TicketsPage/>}/>
      <Route path="/tickets/new" element={<NewTicketPage/>}/>
      <Route path="/tickets/:ticketId" element={<TicketDetailPage/>}/>
      <Route path="/assistant" element={<AssistantPage/>}/>
      <Route path="/assistant/:conversationId" element={<AssistantPage/>}/>
    </Route>
    <Route path="*" element={<Navigate to="/assistant" replace/>}/>
  </Routes></BrowserRouter></AuthProvider></QueryClientProvider>
}
