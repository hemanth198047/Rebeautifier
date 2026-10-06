import { Link, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '../auth'
import { AiStatusChip } from './AiStatusChip'

type NavKey = 'library' | 'search' | 'backup' | 'settings' | 'book'

type Props = {
  children: ReactNode
  active?: NavKey
  compactAi?: boolean
  /** Extra actions rendered before AI chip / user (e.g. save indicator). */
  trailing?: ReactNode
}

const NAV = [
  { key: 'library' as const, to: '/library', label: 'Library' },
  { key: 'search' as const, to: '/search', label: 'Search' },
  { key: 'backup' as const, to: '/backup', label: 'Backup' },
  { key: 'settings' as const, to: '/settings', label: 'Settings' },
]

export function AppShell({ children, active, compactAi = true, trailing }: Props) {
  const { user, logout } = useAuth()
  const location = useLocation()
  const inferred: NavKey =
    active ??
    (location.pathname.startsWith('/search')
      ? 'search'
      : location.pathname.startsWith('/backup')
        ? 'backup'
        : location.pathname.startsWith('/settings')
          ? 'settings'
          : location.pathname.startsWith('/books/')
            ? 'book'
            : 'library')

  return (
    <div className="app-shell app-shell-dense">
      <header className="topbar topbar-dense">
        <div className="topbar-left">
          <Link to="/library" className="brand">
            Rebeautifier<span>.</span>
          </Link>
          <nav className="topbar-nav" aria-label="Primary">
            {NAV.map((item) => (
              <Link
                key={item.key}
                to={item.to}
                className={`topbar-nav-link${inferred === item.key ? ' is-active' : ''}`}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
        <div className="topbar-actions">
          {trailing}
          <AiStatusChip compact={compactAi} />
          <div className="topbar-user">
            <span className="topbar-user-name">{user?.displayName}</span>
            <span className="topbar-user-meta">Signed in</span>
          </div>
          <button className="btn btn-ghost" type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>
      {children}
    </div>
  )
}

type PageHeaderProps = {
  eyebrow?: string
  title: string
  subtitle?: string
  actions?: ReactNode
  meta?: Array<{ label: string; value: string | number }>
}

export function PageHeader({ eyebrow, title, subtitle, actions, meta }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div className="page-header-main">
        {eyebrow && <p className="page-eyebrow">{eyebrow}</p>}
        <div className="page-header-row">
          <div>
            <h1>{title}</h1>
            {subtitle && <p className="muted page-subtitle">{subtitle}</p>}
          </div>
          {actions && <div className="page-header-actions">{actions}</div>}
        </div>
        {meta && meta.length > 0 && (
          <dl className="page-meta-strip">
            {meta.map((item) => (
              <div key={item.label}>
                <dt>{item.label}</dt>
                <dd>{item.value}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </div>
  )
}
