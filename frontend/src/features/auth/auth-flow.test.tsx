// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../../App'

const identity = { id: 4, email: 'member@example.test', role: 'CUSTOMER' }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState({}, '', '/') })

it('protects routes, signs in, restores a cookie session after remount, and signs out', async () => {
  let signedIn = false
  const calls: string[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push(`${init?.method || 'GET'} ${url}`)
    if (url === '/api/auth/csrf') return json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
    if (url === '/api/auth/me') return signedIn ? json(identity) : json({ detail: 'Unauthorized' }, 401)
    if (url === '/api/auth/refresh') return json({ detail: 'Invalid refresh token' }, 401)
    if (url === '/orders?page=0&size=10') return json({ content: [{ id: 7, customerId: 4, status: 'PENDING', createdAt: '2026-10-15T12:00:00Z', updatedAt: '2026-10-15T12:00:00Z' }], page: 0, size: 10, totalElements: 1, totalPages: 1 })
    if (url === '/api/auth/login') { signedIn = true; return json({ user: identity }) }
    if (url === '/api/auth/logout') { signedIn = false; return new Response(null, { status: 204 }) }
    throw new Error(`Unexpected request: ${url}`)
  }))
  window.history.replaceState({}, '', '/orders')
  const first = render(<App />)
  await screen.findByRole('heading', { name: 'Welcome back' })
  expect(window.location.pathname).toBe('/login')
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email address'), identity.email)
  await user.type(screen.getByLabelText('Password'), 'password123')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
  await screen.findByRole('heading', { name: 'Orders' })
  await screen.findByRole('link', { name: '#7' })
  expect(window.location.pathname).toBe('/orders')
  first.unmount()

  render(<App />)
  await screen.findByRole('heading', { name: 'Orders' })
  await screen.findByRole('link', { name: '#7' })
  expect(calls.filter(call => call === 'GET /api/auth/me')).toHaveLength(2)
  await user.click(screen.getByRole('button', { name: 'Sign out' }))
  await screen.findByRole('heading', { name: 'Welcome back' })
  await waitFor(() => expect(window.location.pathname).toBe('/login'))
  expect(calls).toContain('POST /api/auth/logout')
})
