// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../../App'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const longReply = `Order #1001 is currently pending.\n\n${'Item detail line. '.repeat(60)}`

function stubChat(messages: unknown[]) {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url === '/api/auth/me') return json({ id: 4, email: 'member@example.test', role: 'CUSTOMER' })
    if (url === '/agent/conversations?page=0&size=30') return json({ content: [{ id: 12, userId: 4, createdAt: '2026-10-15T12:00:00Z' }], page: 0, size: 30, totalElements: 1, totalPages: 1 })
    if (url === '/agent/conversations/12?page=0&size=100') return json({ conversation: { id: 12, userId: 4, createdAt: '2026-10-15T12:00:00Z' }, messages, page: 0, size: 100, totalElements: messages.length, totalPages: 1 })
    throw new Error(`Unexpected request: ${url}`)
  }))
}

// user-event installs its own clipboard stub during setup(), so stub after it.
function stubClipboard(writeText: () => Promise<void>) {
  Object.defineProperty(navigator, 'clipboard', { value: { writeText: vi.fn(writeText) }, configurable: true, writable: true })
}

const messages = [
  { id: 1, conversationId: 12, role: 'USER', content: 'Show my recent orders', createdAt: '2026-10-15T11:59:00Z' },
  { id: 2, conversationId: 12, role: 'ASSISTANT', content: longReply, createdAt: '2026-10-15T12:00:00Z' },
]

afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); window.history.replaceState({}, '', '/') })

async function openConversation() {
  window.history.replaceState({}, '', '/assistant/12')
  render(<App/>)
  await screen.findByText('Order #1001 is currently pending.', { exact: false })
}

it('copies the full assistant message, confirms, then restores the button', async () => {
  const user = userEvent.setup()
  stubChat(messages)
  stubClipboard(async () => {})
  await openConversation()

  const copies = screen.getAllByRole('button', { name: 'Copy message' })
  expect(copies).toHaveLength(1)

  await user.click(copies[0])
  expect(navigator.clipboard.writeText).toHaveBeenCalledWith(longReply)
  const confirmation = await screen.findByRole('button', { name: 'Message copied' })
  expect(confirmation.textContent).toContain('Copied!')

  await waitFor(() => expect(screen.getByRole('button', { name: 'Copy message' }).textContent).toContain('Copy'), { timeout: 3000 })
})

it('reports a failed copy without breaking the thread', async () => {
  const user = userEvent.setup()
  stubChat(messages)
  stubClipboard(async () => { throw new Error('denied') })
  await openConversation()

  await user.click(screen.getByRole('button', { name: 'Copy message' }))
  const failed = await screen.findByText('Copy failed')
  expect(failed).toBeTruthy()
  expect(screen.getByText('Order #1001 is currently pending.', { exact: false })).toBeTruthy()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Copy message' }).textContent).toContain('Copy'), { timeout: 3000 })
})

it('shows no copy button on user messages', async () => {
  userEvent.setup()
  stubChat(messages)
  stubClipboard(async () => {})
  await openConversation()

  expect(screen.getAllByRole('button', { name: 'Copy message' })).toHaveLength(1)
  expect(screen.getByText('Show my recent orders').closest('.chat-message')?.querySelector('.message-actions')).toBeNull()
})
