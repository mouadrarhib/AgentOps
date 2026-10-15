// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../../App'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState({}, '', '/') })

it('sends a real chat request, shows the returned message, and reports the actual tool count', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init })
    if (url === '/api/auth/me') return json({ id: 4, email: 'member@example.test', role: 'CUSTOMER' })
    if (url === '/agent/conversations?page=0&size=30') return json({ content: [], page: 0, size: 30, totalElements: 0, totalPages: 0 })
    if (url === '/api/auth/csrf') return json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
    if (url === '/agent/chat') return json({ conversationId: 12, toolCalls: 1, reply: { id: 34, conversationId: 12, role: 'ASSISTANT', content: 'Your order is pending.', createdAt: '2026-10-15T12:00:00Z' } })
    if (url === '/agent/conversations/12?page=0&size=100') return json({ conversation: { id: 12, userId: 4, createdAt: '2026-10-15T12:00:00Z' }, messages: [{ id: 33, conversationId: 12, role: 'USER', content: 'Show my recent orders', createdAt: '2026-10-15T11:59:00Z' }, { id: 34, conversationId: 12, role: 'ASSISTANT', content: 'Your order is pending.', createdAt: '2026-10-15T12:00:00Z' }], page: 0, size: 100, totalElements: 2, totalPages: 1 })
    throw new Error(`Unexpected request: ${url}`)
  }))
  window.history.replaceState({}, '', '/assistant')
  render(<App/>)
  await screen.findByRole('heading', { name: 'Ask AgentOps' })
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: /Show my recent orders/i }))
  await user.click(screen.getByRole('button', { name: 'Send message' }))
  await screen.findByText('Your order is pending.')
  expect(screen.getByText('1 backend tool call in the last reply')).toBeTruthy()
  await waitFor(() => expect(window.location.pathname).toBe('/assistant/12'))
  const sent = calls.find(call => call.url === '/agent/chat')
  expect(sent?.init?.method).toBe('POST')
  expect(sent?.init?.body).toBe(JSON.stringify({ message: 'Show my recent orders' }))
  expect(new Headers(sent?.init?.headers).get('X-XSRF-TOKEN')).toBe('csrf-test')
})
