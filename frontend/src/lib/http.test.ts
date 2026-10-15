// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { request } from './http'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => vi.unstubAllGlobals())

it('uses cookies and a fresh CSRF token for login without storing response tokens', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-1' }))
    .mockResolvedValueOnce(json({ user: { id: 1, email: 'a@example.test', role: 'CUSTOMER' }, accessToken: 'secret', refreshToken: 'secret' }))
  vi.stubGlobal('fetch', fetchMock)
  await request('/api/auth/login', { method: 'POST', body: JSON.stringify({ email: 'a@example.test', password: 'password123' }) }, false)
  expect(fetchMock).toHaveBeenCalledTimes(2)
  expect(fetchMock.mock.calls[0][1].credentials).toBe('include')
  const login = fetchMock.mock.calls[1][1]
  expect(login.credentials).toBe('include')
  expect(login.headers.get('X-XSRF-TOKEN')).toBe('csrf-1')
  expect(login.headers.has('Authorization')).toBe(false)
})

it('refreshes a rejected cookie session once and retries the protected read', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(json({ status: 401, detail: 'Unauthorized' }, 401))
    .mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-2' }))
    .mockResolvedValueOnce(json({ user: { id: 1 }, accessToken: 'rotated' }))
    .mockResolvedValueOnce(json({ id: 1, email: 'a@example.test', role: 'CUSTOMER' }))
  vi.stubGlobal('fetch', fetchMock)
  const user = await request<{ id: number }>('/api/auth/me')
  expect(user.id).toBe(1)
  expect(fetchMock.mock.calls.map(call => call[0])).toEqual(['/api/auth/me', '/api/auth/csrf', '/api/auth/refresh', '/api/auth/me'])
  expect(fetchMock.mock.calls[2][1].headers['X-XSRF-TOKEN']).toBe('csrf-2')
  expect(fetchMock.mock.calls[2][1].credentials).toBe('include')
})

it('does not loop when refresh fails', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(json({ detail: 'Unauthorized' }, 401))
    .mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-3' }))
    .mockResolvedValueOnce(json({ detail: 'Invalid refresh token' }, 401))
  vi.stubGlobal('fetch', fetchMock)
  await expect(request('/api/auth/me')).rejects.toMatchObject({ status: 401 })
  expect(fetchMock).toHaveBeenCalledTimes(3)
})
