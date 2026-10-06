import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api, ApiError, type User } from '../api'
import { useAuth } from '../auth'
import { AuthLandingLayout } from '../components/AuthLandingLayout'

export function RegisterPage() {
  const { user, setUser, loading } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!loading && user) {
    return <Navigate to="/library" replace />
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const me = await api<User>('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({ username, email, displayName, password }),
      })
      setUser(me)
      navigate('/library')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to register')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLandingLayout mode="register">
      <h1>Create account</h1>
      <p>Local to this installation — no cloud account required.</p>
      {error && <div className="error-banner">{error}</div>}
      <form className="form-stack" onSubmit={onSubmit}>
        <label>
          Display name
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required maxLength={120} />
        </label>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} required minLength={3} />
        </label>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
        </label>
        <button className="btn btn-primary" type="submit" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create account'}
        </button>
      </form>
      <p className="landing-auth-switch">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthLandingLayout>
  )
}
