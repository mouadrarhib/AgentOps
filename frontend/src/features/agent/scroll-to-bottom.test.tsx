// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../../App'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); window.history.replaceState({}, '', '/') })

const messages = Array.from({ length: 40 }, (_, index) => ({
  id: index + 1,
  conversationId: 12,
  role: index % 2 === 0 ? 'USER' : 'ASSISTANT',
  content: `Message ${index + 1}`,
  createdAt: '2026-10-15T12:00:00Z',
}))

function stubChat() {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url === '/api/auth/me') return json({ id: 4, email: 'member@example.test', role: 'CUSTOMER' })
    if (url === '/agent/conversations?page=0&size=30') return json({ content: [{ id: 12, userId: 4, createdAt: '2026-10-15T12:00:00Z' }], page: 0, size: 30, totalElements: 1, totalPages: 1 })
    if (url === '/agent/conversations/12?page=0&size=100') return json({ conversation: { id: 12, userId: 4, createdAt: '2026-10-15T12:00:00Z' }, messages, page: 0, size: 100, totalElements: messages.length, totalPages: 1 })
    throw new Error(`Unexpected request: ${url}`)
  }))
}

function stubThreadMetrics(thread: HTMLElement) {
  Object.defineProperty(thread, 'scrollHeight', { configurable: true, value: 2000 })
  Object.defineProperty(thread, 'clientHeight', { configurable: true, value: 500 })
}

async function openConversation() {
  window.history.replaceState({}, '', '/assistant/12')
  render(<App/>)
  await screen.findByText('Message 40')
  const thread = document.querySelector('.chat-thread') as HTMLElement
  stubThreadMetrics(thread)
  return thread
}

it('shows the floating button when scrolled away and hides it at the bottom', async () => {
  stubChat()
  const thread = await openConversation()
  expect(screen.queryByRole('button', { name: 'Scroll to latest message' })).toBeNull()

  thread.scrollTop = 200
  fireEvent.scroll(thread)
  expect(screen.getByRole('button', { name: 'Scroll to latest message' })).toBeTruthy()

  thread.scrollTop = 1500
  fireEvent.scroll(thread)
  expect(screen.queryByRole('button', { name: 'Scroll to latest message' })).toBeNull()
})

it('smoothly scrolls to the latest message and hides itself when clicked', async () => {
  const user = userEvent.setup()
  stubChat()
  const thread = await openConversation()
  const scrollTo = vi.fn()
  Object.defineProperty(thread, 'scrollTo', { configurable: true, value: scrollTo })

  thread.scrollTop = 200
  fireEvent.scroll(thread)
  await user.click(screen.getByRole('button', { name: 'Scroll to latest message' }))

  expect(scrollTo).toHaveBeenCalledWith({ top: 2000, behavior: 'smooth' })
  expect(screen.queryByRole('button', { name: 'Scroll to latest message' })).toBeNull()
})

it('keeps the copy message action available while the button is shown', async () => {
  stubChat()
  const thread = await openConversation()
  thread.scrollTop = 200
  fireEvent.scroll(thread)

  expect(screen.getAllByRole('button', { name: 'Copy message' })).toHaveLength(20)
  expect(screen.getByRole('button', { name: 'Scroll to latest message' })).toBeTruthy()
})
