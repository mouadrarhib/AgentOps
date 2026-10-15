import { ChatCircleDots, CirclesThree, Package, Ticket } from '@phosphor-icons/react'

const pages = {
  overview: { title: 'Overview', description: 'Your support workspace is ready.', icon: CirclesThree },
  orders: { title: 'Orders', description: 'Order information will be available in Task 2.', icon: Package },
  tickets: { title: 'Tickets', description: 'Ticket management will be available in Task 3.', icon: Ticket },
  assistant: { title: 'AI Assistant', description: 'Assistant conversations will be available in Task 4.', icon: ChatCircleDots },
}
export function PlaceholderPage({ name }: { name: keyof typeof pages }) {
  const page = pages[name]
  const Icon = page.icon
  return <><div className="page-heading"><span className="section-eyebrow">Workspace</span><h1>{page.title}</h1><p>{page.description}</p></div><div className="placeholder"><div className="placeholder-icon"><Icon size={30}/></div><h2>{page.title} is coming next</h2><p>This section has its layout in place. No account data is shown until its functionality is implemented.</p></div></>
}
