import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight, ShieldCheck, Sparkle } from '@phosphor-icons/react'
import { Button } from '../../components/motion/button/base'
import { useAuth } from './AuthProvider'
import { ApiError } from '../../lib/http'

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, loading, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const destination = (location.state as { from?: string } | null)?.from || '/overview'
  if (loading) return <div className="full-status" role="status">Checking your session…</div>
  if (user) return <Navigate to={destination} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(''); setFieldErrors({})
    if (mode === 'register' && password !== confirm) { setFieldErrors({ confirm: 'Passwords do not match.' }); return }
    setPending(true)
    try {
      await (mode === 'register' ? register(email.trim(), password) : login(email.trim(), password))
      navigate(destination, { replace: true })
    } catch (failure) {
      const problem = failure as Error
      setError(problem.message)
      if (problem instanceof ApiError) setFieldErrors(Object.fromEntries(problem.fields.map(field => [field.field, field.message])))
    } finally { setPending(false) }
  }

  return <div className="auth-page">
    <Link className="auth-logo" to="/login"><span className="brand-mark"><Sparkle size={22} weight="fill" /></span><strong>AgentOps</strong></Link>
    <div className="auth-grid"><section className="auth-intro"><div className="eyebrow"><ShieldCheck size={17} /> Support, with clear boundaries</div><h1>Support work,<br />in focus.</h1><p>One secure place for the conversations, orders, and requests that matter to you.</p><div className="intro-line"><span>01</span> Sign in securely <span>→</span> Work from one workspace</div></section>
      <section className="auth-card" aria-labelledby="auth-title"><div className="card-icon"><Sparkle size={22} weight="fill" /></div><h2 id="auth-title">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2><p>{mode === 'login' ? 'Sign in to your AgentOps workspace.' : 'Register as a customer to get started.'}</p>
        {error && <div className="form-error" role="alert">{error}</div>}
        <form onSubmit={submit}><label htmlFor="email">Email address</label><input id="email" type="email" autoComplete="email" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} aria-invalid={!!fieldErrors.email} aria-describedby={fieldErrors.email ? 'email-error' : undefined} placeholder="you@example.com" />{fieldErrors.email && <small id="email-error" className="field-error">{fieldErrors.email}</small>}
          <label htmlFor="password">Password</label><input id="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'register' ? 8 : undefined} maxLength={128} value={password} onChange={event => setPassword(event.target.value)} aria-invalid={!!fieldErrors.password} aria-describedby={fieldErrors.password ? 'password-error' : undefined} placeholder={mode === 'register' ? 'At least 8 characters' : 'Enter your password'} />{fieldErrors.password && <small id="password-error" className="field-error">{fieldErrors.password}</small>}
          {mode === 'register' && <><label htmlFor="confirm">Confirm password</label><input id="confirm" type="password" autoComplete="new-password" required value={confirm} onChange={event => setConfirm(event.target.value)} aria-invalid={!!fieldErrors.confirm} aria-describedby={fieldErrors.confirm ? 'confirm-error' : undefined} placeholder="Repeat your password" />{fieldErrors.confirm && <small id="confirm-error" className="field-error">{fieldErrors.confirm}</small>}</>}
          <Button type="submit" disabled={pending} className="auth-submit">{pending ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'} <ArrowRight size={17}/></Button></form>
        <div className="auth-switch">{mode === 'login' ? 'New to AgentOps?' : 'Already have an account?'} <Link to={mode === 'login' ? '/register' : '/login'}>{mode === 'login' ? 'Create an account' : 'Sign in'}</Link></div>
      </section></div>
  </div>
}
