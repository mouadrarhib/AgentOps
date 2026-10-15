import type { ReactNode } from 'react'
import { Button } from './motion/button/base'
import { errorMessage } from '../lib/http'
import { humanize } from '../lib/api'

export function PageHeader({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <header className="page-heading"><div><h1>{title}</h1><p>{description}</p></div>{action}</header>
}

export function DataState({ loading, error, onRetry, empty, children }: {
  loading: boolean
  error: Error | null
  onRetry: () => void
  empty: boolean
  children: ReactNode
}) {
  if (loading) return <div className="data-state" role="status"><span className="loading-dot" /> Loading your data…</div>
  if (error) {
    return <div className="data-state data-error" role="alert"><strong>Could not load this page</strong><p>{errorMessage(error)}</p><Button variant="outline" onClick={onRetry}>Try again</Button></div>
  }
  if (empty) return <div className="data-state">{children}</div>
  return <>{children}</>
}

export function StatusBadge({ value }: { value: string }) {
  return <span className={`status-badge status-${value.toLowerCase()}`}>{humanize(value)}</span>
}

export function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return <div className="pager"><Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>Previous</Button><span>Page {page + 1} of {totalPages}</span><Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</Button></div>
}
