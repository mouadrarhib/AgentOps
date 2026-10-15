import { createContext, useContext, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { authApi, type User } from './auth-api'
import { ApiError } from '../../lib/http'

type AuthContextValue = {
  user: User | null
  loading: boolean
  error: Error | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  retry: () => void
}
const AuthContext = createContext<AuthContextValue | null>(null)
const key = ['auth', 'me'] as const

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient()
  const query = useQuery({ queryKey: key, queryFn: authApi.me, retry: false, refetchOnWindowFocus: false })
  const unauthorized = query.error instanceof ApiError && query.error.status === 401
  async function login(email: string, password: string) {
    const result = await authApi.login(email, password)
    client.setQueryData(key, result.user)
  }
  async function logout() {
    await authApi.logout()
    client.setQueryData(key, null)
  }
  return <AuthContext.Provider value={{
    user: query.data ?? null,
    loading: query.isPending && query.isFetching,
    error: unauthorized ? null : query.error,
    login, logout, retry: () => { void query.refetch() },
  }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
