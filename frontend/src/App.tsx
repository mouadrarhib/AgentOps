import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { AuthProvider, useAuth } from './features/auth/AuthProvider'
import { AuthPage } from './features/auth/AuthPage'
import { AppShell } from './features/shell/AppShell'
import { PlaceholderPage } from './features/shell/PlaceholderPage'

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
    <Route path="/login" element={<AuthPage mode="login"/>}/>
    <Route path="/register" element={<AuthPage mode="register"/>}/>
    <Route element={<ProtectedRoute/>}>
      <Route path="/overview" element={<PlaceholderPage name="overview"/>}/>
      <Route path="/orders" element={<PlaceholderPage name="orders"/>}/>
      <Route path="/tickets" element={<PlaceholderPage name="tickets"/>}/>
      <Route path="/assistant" element={<PlaceholderPage name="assistant"/>}/>
    </Route>
    <Route path="*" element={<Navigate to="/overview" replace/>}/>
  </Routes></BrowserRouter></AuthProvider></QueryClientProvider>
}
