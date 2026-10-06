import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './auth'

export function ProtectedRoute() {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <div className="page">
        <p className="muted">Settling in…</p>
      </div>
    )
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}
