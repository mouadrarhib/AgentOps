import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { ChatCircleDots, CirclesThree, List, Package, SignOut, Sparkle, Ticket, X } from '@phosphor-icons/react'
import { Button } from '../../components/motion/button/base'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { useAuth } from '../auth/AuthProvider'
import { errorMessage } from '../../lib/http'

const sections = [
  { to: '/assistant', label: 'AI assistant', icon: ChatCircleDots },
  { to: '/overview', label: 'Overview', icon: CirclesThree },
  { to: '/orders', label: 'Orders', icon: Package },
  { to: '/tickets', label: 'Support tickets', icon: Ticket },
]

export function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirmSignOut, setConfirmSignOut] = useState(false)
  const current = sections.find(item => location.pathname.startsWith(item.to))?.label || 'Workspace'
  async function signOut() {
    setPending(true); setError('')
    try { await logout(); navigate('/login', { replace: true }) }
    catch (failure) { setError(errorMessage(failure)) }
    finally { setConfirmSignOut(false); setPending(false) }
  }
  const navigation = <nav aria-label="Main navigation">{sections.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} onClick={() => setMenuOpen(false)} className={({ isActive }) => `nav-link${to === '/assistant' ? ' nav-assistant' : ''}${isActive || location.pathname.startsWith(`${to}/`) ? ' active' : ''}`}><Icon size={20} weight={location.pathname.startsWith(to) ? 'fill' : 'regular'}/><span>{label}</span></NavLink>)}</nav>
  return <div className="app-shell">
    {menuOpen && <button type="button" className="menu-scrim" onClick={() => setMenuOpen(false)} aria-label="Close navigation"/>}
    <aside id="app-navigation" className={`sidebar${menuOpen ? ' open' : ''}`}><div className="brand"><span className="brand-mark"><Sparkle size={21} weight="fill"/></span><span><strong>Agent<span>Ops</span></strong><small>Support workspace</small></span><button className="mobile-close" type="button" aria-label="Close navigation" onClick={() => setMenuOpen(false)}><X size={20}/></button></div><div className="nav-label">Workspace</div>{navigation}<div className="sidebar-account"><div className="account-avatar">{user?.email[0]?.toUpperCase()}</div><div className="account-copy"><strong>{user?.email}</strong><small>{user?.role.toLowerCase()}</small></div><Button type="button" variant="ghost" size="icon" onClick={() => setConfirmSignOut(true)} disabled={pending} aria-label="Sign out"><SignOut size={19}/></Button></div></aside>
    <div className="app-main"><header className="topbar"><button type="button" className="menu-toggle" onClick={() => setMenuOpen(true)} aria-label="Open navigation" aria-controls="app-navigation" aria-expanded={menuOpen}><List size={23}/></button><strong>{current}</strong><span className="topbar-status"><i/> {user?.role === 'CUSTOMER' ? 'Customer' : user?.role === 'SUPPORT' ? 'Support' : 'Admin'} workspace</span></header>{error && <div className="shell-error" role="alert">{error}</div>}<main className="page-content"><Outlet/></main></div>
    <ConfirmDialog open={confirmSignOut} title="Sign out?" description="You will need to sign in again to access your workspace." confirmLabel="Sign out" pending={pending} onCancel={() => setConfirmSignOut(false)} onConfirm={() => void signOut()}/>
  </div>
}
