import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { ChatCircleDots, CirclesThree, Package, SignOut, Sparkle, Ticket } from '@phosphor-icons/react'
import { useAuth } from '../auth/AuthProvider'

const sections = [
  { to: '/overview', label: 'Overview', icon: CirclesThree },
  { to: '/orders', label: 'Orders', icon: Package },
  { to: '/tickets', label: 'Tickets', icon: Ticket },
  { to: '/assistant', label: 'AI Assistant', icon: ChatCircleDots },
]

export function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  async function signOut() {
    setPending(true); setError('')
    try { await logout(); navigate('/login', { replace: true }) }
    catch (failure) { setError((failure as Error).message) }
    finally { setPending(false) }
  }
  return <div className="app-shell"><aside className="sidebar"><div className="brand"><span className="brand-mark"><Sparkle size={21} weight="fill" /></span><span><strong>AgentOps</strong><small>Support workspace</small></span></div><div className="nav-label">Workspace</div><nav aria-label="Main navigation">{sections.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}><Icon size={20}/><span>{label}</span></NavLink>)}</nav><div className="sidebar-account"><div className="account-avatar">{user?.email[0]?.toUpperCase()}</div><div className="account-copy"><strong>{user?.email}</strong><small>{user?.role.toLowerCase()}</small></div><button type="button" onClick={() => void signOut()} disabled={pending} aria-label="Sign out"><SignOut size={19}/></button></div></aside><div className="app-main"><header className="topbar"><strong>AgentOps</strong><span>Authenticated workspace</span></header>{error && <div className="shell-error" role="alert">{error}</div>}<main className="page-content"><Outlet/></main></div></div>
}
