import { useEffect, useState, type FormEvent } from 'react'
import { ArrowRight, ChatCircleDots, CirclesThree, ClipboardText, Package, Plus, SignOut, Sparkle, Ticket as TicketIcon } from '@phosphor-icons/react'
import { api, setTokens, type Auth, type Conversation, type Message, type Order, type Ticket } from './api'
import { Button } from './components/motion/button/base'

type View = 'overview' | 'assistant' | 'orders' | 'tickets'
const statuses = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
const categories = ['ORDER', 'PAYMENT', 'DELIVERY', 'RETURN', 'OTHER']
const titleCase = (value: string) => value.replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase())
const date = (value?: string) => value ? new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value)) : '—'

function App() {
  const [auth, setAuth] = useState<Auth | null>(null)
  const [view, setView] = useState<View>('overview')
  const [orders, setOrders] = useState<Order[]>([])
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [counts, setCounts] = useState({ orders: 0, tickets: 0, conversations: 0 })
  const [conversationId, setConversationId] = useState<number>()
  const [messages, setMessages] = useState<Message[]>([])
  const [draft, setDraft] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [ticketOpen, setTicketOpen] = useState(false)
  const [orderId, setOrderId] = useState('')
  const [category, setCategory] = useState('ORDER')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [pages, setPages] = useState(1)

  async function load(target: View, targetPage = 0, filter = statusFilter) {
    if (!auth) return
    setError('')
    try {
      if (target === 'overview') {
        const [o, t, c] = await Promise.all([api.orders(), api.tickets(), api.conversations()])
        setOrders(o.content); setTickets(t.content); setConversations(c.content)
        setCounts({ orders: o.totalElements, tickets: t.totalElements, conversations: c.totalElements })
      } else if (target === 'orders') {
        const result = await api.orders(targetPage); setOrders(result.content); setPages(result.totalPages)
      } else if (target === 'tickets') {
        const result = await api.tickets(targetPage, filter); setTickets(result.content); setPages(result.totalPages)
      } else {
        const result = await api.conversations(); setConversations(result.content)
      }
    } catch (e) { setError((e as Error).message) }
  }
  useEffect(() => { if (auth) void load(view, page) }, [auth, view, page])
  async function openConversation(id: number) {
    setError('')
    try { const detail = await api.conversation(id); setConversationId(id); setMessages(detail.messages); setView('assistant') }
    catch (e) { setError((e as Error).message) }
  }
  async function sendMessage(event: FormEvent) {
    event.preventDefault(); if (!draft.trim() || pending) return
    const message = draft.trim(); setDraft(''); setPending(true); setError('')
    const optimistic: Message = { id: -Date.now(), role: 'USER', content: message, createdAt: new Date().toISOString() }
    setMessages(m => [...m, optimistic])
    try { const result = await api.chat(message, conversationId); setConversationId(result.conversationId); setMessages(m => [...m, result.reply]); void load('assistant') }
    catch (e) { setMessages(m => m.filter(x => x.id !== optimistic.id)); setDraft(message); setError((e as Error).message) }
    finally { setPending(false) }
  }
  async function createTicket(event: FormEvent) {
    event.preventDefault(); setPending(true); setError('')
    try { await api.createTicket(Number(orderId), category); setTicketOpen(false); setOrderId(''); await load('tickets', 0); setView('tickets') }
    catch (e) { setError((e as Error).message) }
    finally { setPending(false) }
  }
  async function updateTicket(id: number, status: string) {
    setError('')
    try { await api.updateTicket(id, { status }); await load('tickets', page) }
    catch (e) { setError((e as Error).message) }
  }
  function navigate(next: View) { setPage(0); setView(next); setError('') }
  async function logout() { try { await api.logout() } finally { setTokens(null); setAuth(null); setMessages([]); setConversationId(undefined) } }
  if (!auth) return <AuthScreen onAuth={result => { setTokens(result); setAuth(result) }} />
  const nav = [ { id: 'overview', label: 'Overview', icon: CirclesThree }, { id: 'assistant', label: 'AI assistant', icon: ChatCircleDots }, { id: 'orders', label: 'Orders', icon: Package }, { id: 'tickets', label: 'Tickets', icon: TicketIcon } ] as const
  return <div className="shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Sparkle size={23} weight="fill" /></div><div><strong>AgentOps</strong><span>Support workspace</span></div></div>
      <div className="sidebar-label">Workspace</div>
      <nav aria-label="Main navigation">{nav.map(item => <button key={item.id} className={`nav-item ${view === item.id ? 'active' : ''}`} onClick={() => navigate(item.id)}><item.icon size={20} weight={view === item.id ? 'fill' : 'regular'} />{item.label}</button>)}</nav>
      <div className="sidebar-bottom"><div className="account"><div className="avatar">{auth.user.email[0].toUpperCase()}</div><div className="account-text"><strong>{auth.user.email}</strong><span>{titleCase(auth.user.role)}</span></div></div><button className="logout" onClick={() => void logout()} aria-label="Sign out"><SignOut size={19} /></button></div>
    </aside>
    <main className="main">
      <header className="topbar"><div className="breadcrumb">Workspace <span>/</span> <strong>{nav.find(item => item.id === view)?.label}</strong></div><div className="top-actions"><span className="live-dot" /> Connected to AgentOps API</div></header>
      <div className="content">
        {error && <div className="error" role="alert">{error}<button onClick={() => setError('')}>Dismiss</button></div>}
        {view === 'overview' && <>
          <section className="hero"><div className="hero-copy"><span className="hero-kicker"><Sparkle size={16} weight="fill" /> AI-powered support</span><h1>One place for every<br />support conversation.</h1><p>Find orders, follow tickets, and get answers from an assistant grounded in your account data.</p><Button onClick={() => navigate('assistant')}>Ask the assistant <ArrowRight size={17} /></Button></div><div className="hero-art" aria-hidden="true"><div className="art-orbit orbit-one"/><div className="art-orbit orbit-two"/><div className="art-core"><Sparkle size={42} weight="fill" /></div><div className="art-note note-one">Orders in context</div><div className="art-note note-two">Actions you can trust</div></div></section>
          <div className="section-head"><div><h2>At a glance</h2><p>Your current workspace activity</p></div></div>
          <div className="stats"><button onClick={() => navigate('orders')}><Package size={22}/><span>Orders</span><strong>{counts.orders}</strong><small>View orders <ArrowRight size={14}/></small></button><button onClick={() => navigate('tickets')}><TicketIcon size={22}/><span>Tickets</span><strong>{counts.tickets}</strong><small>View tickets <ArrowRight size={14}/></small></button><button onClick={() => navigate('assistant')}><ChatCircleDots size={22}/><span>Conversations</span><strong>{counts.conversations}</strong><small>Open assistant <ArrowRight size={14}/></small></button></div>
          <div className="section-head"><div><h2>Recent tickets</h2><p>Follow up where it matters</p></div><button className="text-button" onClick={() => navigate('tickets')}>View all <ArrowRight size={16}/></button></div>
          <TicketList tickets={tickets.slice(0, 4)} staff={auth.user.role !== 'CUSTOMER'} onUpdate={updateTicket}/>
        </>}
        {view === 'orders' && <><div className="page-head"><div><h1>Orders</h1><p>Track the orders available to your account.</p></div></div><div className="table-card"><div className="table-head"><span>Order</span><span>Status</span><span>Customer</span><span>Created</span></div>{orders.length ? orders.map(order => <div className="table-row" key={order.id}><strong>#{order.id}</strong><span><Status value={order.status}/></span><span>#{order.customerId}</span><span>{date(order.createdAt)}</span></div>) : <Empty icon={<Package size={27}/>} title="No orders yet" text="Orders you can access will appear here."/>}</div><Pagination page={page} pages={pages} setPage={setPage}/></>}
        {view === 'tickets' && <><div className="page-head"><div><h1>Tickets</h1><p>Review requests and keep support moving.</p></div><Button onClick={() => setTicketOpen(true)}><Plus size={18}/> New ticket</Button></div><div className="filter-row"><label htmlFor="status">Status</label><select id="status" value={statusFilter} onChange={e => {setStatusFilter(e.target.value);setPage(0);void load('tickets', 0, e.target.value)}}><option value="">All statuses</option>{statuses.map(s => <option key={s} value={s}>{titleCase(s)}</option>)}</select></div><TicketList tickets={tickets} staff={auth.user.role !== 'CUSTOMER'} onUpdate={updateTicket}/><Pagination page={page} pages={pages} setPage={setPage}/></>}
        {view === 'assistant' && <div className="assistant-layout"><div className="conversation-side"><div className="conversation-head"><h2>Conversations</h2><button aria-label="New conversation" onClick={() => {setConversationId(undefined);setMessages([])}}><Plus size={19}/></button></div>{conversations.length ? conversations.map(c => <button key={c.id} className={`conversation-item ${conversationId === c.id ? 'selected' : ''}`} onClick={() => void openConversation(c.id)}><ChatCircleDots size={18}/><span>Conversation #{c.id}<small>{date(c.createdAt)}</small></span></button>) : <p className="quiet">Your conversations will appear here.</p>}</div><div className="chat-panel"><div className="chat-head"><div className="chat-icon"><Sparkle size={19} weight="fill"/></div><div><strong>AgentOps assistant</strong><span>Answers grounded in your orders and tickets</span></div></div><div className="messages">{messages.length ? messages.map(m => <div className={`message ${m.role === 'USER' ? 'mine' : ''}`} key={m.id}><div className="message-author">{m.role === 'USER' ? 'You' : 'AgentOps'}</div><p>{m.content}</p></div>) : <div className="chat-empty"><div className="chat-empty-icon"><Sparkle size={30} weight="fill"/></div><h2>How can I help?</h2><p>Ask about an order, check a ticket, or create a support request in plain language.</p><div className="suggestions">{['Show me my orders', 'What is the status of my tickets?'].map(s => <button key={s} onClick={() => setDraft(s)}>{s} <ArrowRight size={15}/></button>)}</div></div>}{pending && <div className="thinking" role="status">Assistant is working<span className="dots">...</span></div>}</div><form className="composer" onSubmit={sendMessage}><label htmlFor="message" className="sr-only">Message the assistant</label><textarea id="message" rows={2} maxLength={4000} value={draft} onChange={e => setDraft(e.target.value)} onKeyDown={e => {if (e.key === 'Enter' && !e.shiftKey) {e.preventDefault();e.currentTarget.form?.requestSubmit()}}} placeholder="Ask about an order or ticket..."/><Button type="submit" disabled={pending || !draft.trim()} aria-label="Send message"><ArrowRight size={19}/></Button></form><p className="composer-note">Agent actions follow your account permissions. Review details before requesting changes.</p></div></div>}
      </div>
    </main>
    {ticketOpen && <div className="modal-backdrop" onMouseDown={e => {if (e.target === e.currentTarget) setTicketOpen(false)}}><div className="modal" role="dialog" aria-modal="true" aria-labelledby="ticket-title"><div className="modal-head"><div><h2 id="ticket-title">New support ticket</h2><p>Enter an order number and choose the topic.</p></div><button onClick={() => setTicketOpen(false)} aria-label="Close">×</button></div><form onSubmit={createTicket}><label>Order number<input type="number" min="1" required list="known-orders" value={orderId} onChange={e => setOrderId(e.target.value)} placeholder="For example, 12"/><datalist id="known-orders">{orders.map(o => <option key={o.id} value={o.id}/>)}</datalist></label><label>Category<select value={category} onChange={e => setCategory(e.target.value)}>{categories.map(c => <option key={c}>{c}</option>)}</select></label><div className="modal-actions"><button type="button" className="secondary" onClick={() => setTicketOpen(false)}>Cancel</button><Button type="submit" disabled={pending || !orderId}>Create ticket</Button></div></form></div></div>}
  </div>
}

function AuthScreen({ onAuth }: { onAuth: (auth: Auth) => void }) {
  const [register, setRegister] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  async function submit(event: FormEvent) {event.preventDefault();setPending(true);setError('');try {onAuth(await (register ? api.register(email, password) : api.login(email, password)))} catch (e) {setError((e as Error).message)} finally {setPending(false)}}
  return <div className="auth-screen"><div className="auth-brand"><div className="brand-mark"><Sparkle size={23} weight="fill"/></div><strong>AgentOps</strong></div><div className="auth-layout"><div className="auth-story"><span className="hero-kicker"><Sparkle size={16} weight="fill"/> A smarter support experience</span><h1>Support work,<br/>in focus.</h1><p>Orders, tickets, and an AI assistant connected to the information you’re allowed to see.</p><div className="auth-graphic"><div className="graphic-line"><Package size={19}/> Order context <span>Connected</span></div><div className="graphic-line"><ChatCircleDots size={19}/> AI assistant <span>Ready to help</span></div><div className="graphic-line"><ClipboardText size={19}/> Support requests <span>In one place</span></div></div></div><div className="auth-card"><div className="card-icon"><Sparkle size={22} weight="fill"/></div><h2>{register ? 'Create your account' : 'Welcome back'}</h2><p>{register ? 'Get started with AgentOps support.' : 'Sign in to your support workspace.'}</p>{error && <div className="error" role="alert">{error}</div>}<form onSubmit={submit}><label>Email address<input type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com"/></label><label>Password<input type="password" autoComplete={register ? 'new-password' : 'current-password'} minLength={register ? 8 : undefined} required value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter your password"/></label><Button type="submit" disabled={pending}>{pending ? 'Please wait…' : register ? 'Create account' : 'Sign in'} <ArrowRight size={18}/></Button></form><div className="auth-switch">{register ? 'Already have an account?' : 'New to AgentOps?'} <button onClick={() => {setRegister(!register);setError('')}}>{register ? 'Sign in' : 'Create an account'}</button></div></div></div></div>
}

function Status({ value }: { value: string }) { return <span className={`status status-${value.toLowerCase()}`}><i/>{titleCase(value)}</span> }
function Empty({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) { return <div className="empty"><div>{icon}</div><h3>{title}</h3><p>{text}</p></div> }
function Pagination({ page, pages, setPage }: { page: number; pages: number; setPage: (page: number) => void }) { return pages > 1 ? <div className="pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {pages}</span><button disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</button></div> : null }
function TicketList({ tickets, staff, onUpdate }: { tickets: Ticket[]; staff: boolean; onUpdate: (id: number, status: string) => void }) { return <div className="ticket-list">{tickets.length ? tickets.map(t => <div className="ticket-row" key={t.id}><div className="ticket-symbol"><TicketIcon size={21}/></div><div className="ticket-main"><strong>Ticket #{t.id} <span>·</span> {titleCase(t.category)}</strong><small>Order #{t.orderId} · {titleCase(t.priority)} priority</small></div><Status value={t.status}/>{staff && <select aria-label={`Update ticket ${t.id} status`} value={t.status} onChange={e => onUpdate(t.id, e.target.value)}>{statuses.map(s => <option key={s} value={s}>{titleCase(s)}</option>)}</select>}</div>) : <Empty icon={<TicketIcon size={27}/>} title="No tickets found" text="Create a ticket for an order, or try another status filter."/>}</div> }

export default App


