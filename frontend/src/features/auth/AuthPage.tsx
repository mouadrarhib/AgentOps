import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight, ShieldCheck, Sparkle } from '@phosphor-icons/react'
import { Button } from '../../components/motion/button/base'
import { useAuth } from './AuthProvider'
import { ApiError } from '../../lib/http'

export function AuthPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const destination = (location.state as { from?: string } | null)?.from || '/assistant'
  if (loading) return <div className="full-status" role="status">Checking your session…</div>
  if (user) return <Navigate to={destination} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(''); setFieldErrors({})
    setPending(true)
    try {
      await login(email.trim(), password)
      navigate(destination, { replace: true })
    } catch (failure) {
      const problem = failure as Error
      setError(problem.message)
      if (problem instanceof ApiError) setFieldErrors(Object.fromEntries(problem.fields.map(field => [field.field, field.message])))
    } finally { setPending(false) }
  }

  return <div className="auth-page">
    <Link className="auth-logo" to="/login"><span className="brand-mark"><Sparkle size={22} weight="fill" /></span><strong>AgentOps</strong></Link>
    <div className="auth-grid"><section className="auth-intro"><div className="eyebrow"><ShieldCheck size={17} /> Support, with clear boundaries</div><h1>Answers about your orders,<br />all in one place.</h1><p>Check order details, follow support tickets, and ask the AgentOps assistant for help.</p><div className="intro-line"><span>01</span> Sign in securely <span>→</span> Ask AgentOps for help</div></section>
      <section className="auth-card" aria-labelledby="auth-title"><div className="card-icon"><Sparkle size={22} weight="fill" /></div><h2 id="auth-title">Welcome back</h2><p>Sign in to check orders, follow tickets, and chat with AgentOps.</p>
        {error && <div className="form-error" role="alert">{error}</div>}
        <form onSubmit={submit}><label htmlFor="email">Email address</label><input id="email" type="email" autoComplete="email" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} aria-invalid={!!fieldErrors.email} aria-describedby={fieldErrors.email ? 'email-error' : undefined} placeholder="you@example.com" />{fieldErrors.email && <small id="email-error" className="field-error">{fieldErrors.email}</small>}
          <label htmlFor="password">Password</label><input id="password" type="password" autoComplete="current-password" required maxLength={128} value={password} onChange={event => setPassword(event.target.value)} aria-invalid={!!fieldErrors.password} aria-describedby={fieldErrors.password ? 'password-error' : undefined} placeholder="Enter your password" />{fieldErrors.password && <small id="password-error" className="field-error">{fieldErrors.password}</small>}
          <Button type="submit" disabled={pending} className="auth-submit">{pending ? 'Please wait…' : 'Open my workspace'} <ArrowRight size={17}/></Button></form>
      </section></div>
  </div>
}
