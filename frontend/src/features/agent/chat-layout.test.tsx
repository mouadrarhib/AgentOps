// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import App from '../../App'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState({}, '', '/') })

const messages = Array.from({ length: 40 }, (_, index) => ({
  id: index + 1,
  conversationId: 12,
  role: index % 2 === 0 ? 'USER' : 'ASSISTANT',
  content: `Message ${index + 1}`,
  createdAt: '2026-10-15T12:00:00Z',
}))

it('keeps the composer outside the scrollable thread, anchored after it in the chat panel', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url === '/api/auth/me') return json({ id: 4, email: 'member@example.test', role: 'CUSTOMER' })
    if (url === '/agent/conversations?page=0&size=30') return json({ content: [{ id: 12, userId: 4, createdAt: '2026-10-15T12:00:00Z' }], page: 0, size: 30, totalElements: 1, totalPages: 1 })
    if (url === '/agent/conversations/12?page=0&size=100') return json({ conversation: { id: 12, userId: 4, createdAt: '2026-10-15T12:00:00Z' }, messages, page: 0, size: 100, totalElements: messages.length, totalPages: 1 })
    throw new Error(`Unexpected request: ${url}`)
  }))
  window.history.replaceState({}, '', '/assistant/12')
  render(<App/>)
  await screen.findByText('Message 40')

  const panel = document.querySelector('.chat-panel')!
  const [header, thread, composer] = Array.from(panel.children)
  expect(header.className).toBe('chat-panel-top')
  expect(thread.className).toBe('chat-thread')
  expect(composer.tagName).toBe('FORM')
  expect(composer.className).toContain('chat-composer')

  expect(thread.querySelectorAll('.chat-message')).toHaveLength(40)
  expect(thread.contains(composer)).toBe(false)
  expect(composer.querySelector('button[aria-label="Send message"]')).toBeTruthy()
})
