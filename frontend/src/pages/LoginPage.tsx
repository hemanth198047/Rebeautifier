import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api, ApiError, type User } from '../api'
import { useAuth } from '../auth'
import { AuthLandingLayout } from '../components/AuthLandingLayout'

export function LoginPage() {
  const { user, setUser, loading } = useAuth()
  const navigate = useNavigate()
  const [usernameOrEmail, setUsernameOrEmail] = useState('reader')
  const [password, setPassword] = useState('password123')
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
      const me = await api<User>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ usernameOrEmail, password }),
      })
      setUser(me)
      navigate('/library')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to sign in')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLandingLayout mode="login">
      <h1>Sign in</h1>
      <p>Use your username or email for this install.</p>
      {error && <div className="error-banner">{error}</div>}
      <form className="form-stack" onSubmit={onSubmit}>
        <label>
          Username or email
          <input
            value={usernameOrEmail}
            onChange={(e) => setUsernameOrEmail(e.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <button className="btn btn-primary" type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="landing-auth-switch">
        New here? <Link to="/register">Create an account</Link>
      </p>
    </AuthLandingLayout>
  )
}
