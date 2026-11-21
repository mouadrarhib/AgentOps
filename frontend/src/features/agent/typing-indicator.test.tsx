// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../../App'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState({}, '', '/') })

it('shows the assistant writing indicator while the reply is in flight and clears it after', async () => {
  let release!: () => void
  const inFlight = new Promise<void>(resolve => { release = resolve })
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url === '/api/auth/me') return json({ id: 4, email: 'member@example.test', role: 'CUSTOMER' })
    if (url === '/agent/conversations?page=0&size=30') return json({ content: [], page: 0, size: 30, totalElements: 0, totalPages: 0 })
    if (url === '/api/auth/csrf') return json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
    if (url === '/agent/chat') {
      await inFlight
      return json({ conversationId: 12, toolCalls: 0, reply: { id: 34, conversationId: 12, role: 'ASSISTANT', content: 'Your order is pending.', createdAt: '2026-10-15T12:00:00Z' } })
    }
    if (url === '/agent/conversations/12?page=0&size=100') return json({ conversation: { id: 12, userId: 4, createdAt: '2026-10-15T12:00:00Z' }, messages: [{ id: 33, conversationId: 12, role: 'USER', content: 'Show my recent orders', createdAt: '2026-10-15T11:59:00Z' }, { id: 34, conversationId: 12, role: 'ASSISTANT', content: 'Your order is pending.', createdAt: '2026-10-15T12:00:00Z' }], page: 0, size: 100, totalElements: 2, totalPages: 1 })
    throw new Error(`Unexpected request: ${url}`)
  }))
  window.history.replaceState({}, '', '/assistant')
  render(<App/>)
  await screen.findByRole('heading', { name: 'Ask AgentOps' })
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: /Show my recent orders/i }))
  await user.click(screen.getByRole('button', { name: 'Send message' }))

  const status = await screen.findByRole('status')
  expect(status.textContent).toContain('writing a reply')
  const dots = status.querySelectorAll('.typing-dot')
  expect(dots).toHaveLength(3)
  expect(dots[0].closest('.typing-dots')?.getAttribute('aria-hidden')).toBe('true')

  release()
  await screen.findByText('Your order is pending.')
  await waitFor(() => expect(screen.queryByRole('status')).toBeNull())
})
