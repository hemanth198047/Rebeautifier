import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, type AiStatus, type RateLimitStatus, type User } from '../api'
import { useAuth } from '../auth'
import { AiStatusChip } from '../components/AiStatusChip'

type Health = {
  status: string
  service: string
  version?: string
}

type Ready = Health & {
  database: string
}

export function SettingsPage() {
  const { user, logout, setUser } = useAuth()
  const [ai, setAi] = useState<AiStatus | null>(null)
  const [limits, setLimits] = useState<RateLimitStatus | null>(null)
  const [health, setHealth] = useState<Health | null>(null)
  const [ready, setReady] = useState<Ready | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [passwordMsg, setPasswordMsg] = useState<string | null>(null)
  const [profileMsg, setProfileMsg] = useState<string | null>(null)
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)
  const [profileBusy, setProfileBusy] = useState(false)

  useEffect(() => {
    setDisplayName(user?.displayName ?? '')
  }, [user?.displayName])

  async function refreshStatus() {
    setError(null)
    try {
      const [aiStatus, limitStatus, healthStatus, readyStatus] = await Promise.all([
        api<AiStatus>('/api/v1/system/ai-status'),
        api<RateLimitStatus>('/api/v1/system/rate-limits'),
        fetch('/api/v1/health').then(async (r) => (await r.json()) as Health),
        fetch('/api/v1/health/ready').then(async (r) => (await r.json()) as Ready),
      ])
      setAi(aiStatus)
      setLimits(limitStatus)
      setHealth(healthStatus)
      setReady(readyStatus)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load system status')
    }
  }

  useEffect(() => {
    void refreshStatus()
  }, [])

  async function onSaveProfile(event: FormEvent) {
    event.preventDefault()
    setProfileBusy(true)
    setProfileMsg(null)
    setError(null)
    try {
      const updated = await api<User>('/api/v1/auth/me', {
        method: 'PATCH',
        body: JSON.stringify({ displayName }),
      })
      setUser(updated)
      setProfileMsg('Display name updated')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update profile')
    } finally {
      setProfileBusy(false)
    }
  }

  async function onChangePassword(event: FormEvent) {
    event.preventDefault()
    setPasswordBusy(true)
    setPasswordMsg(null)
    setError(null)
    try {
      await api('/api/v1/auth/change-password', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword }),
      })
      setCurrentPassword('')
      setNewPassword('')
      setPasswordMsg('Password updated')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change password')
    } finally {
      setPasswordBusy(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/library">
          Rebeautifier<span>.</span>
        </Link>
        <div className="topbar-actions">
          <Link className="btn btn-ghost" to="/library">
            Library
          </Link>
          <Link className="btn btn-ghost" to="/search">
            Search
          </Link>
          <Link className="btn btn-ghost" to="/backup">
            Backup
          </Link>
          <AiStatusChip />
          <span className="muted">{user?.displayName}</span>
          <button className="btn btn-ghost" type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="page settings-page">
        <div className="library-header">
          <div>
            <h1>Settings</h1>
            <p className="muted">Account, install health, and local AI setup — all stay on this host.</p>
          </div>
          <button className="btn btn-ghost" type="button" onClick={() => void refreshStatus()}>
            Refresh status
          </button>
        </div>

        {error && <div className="error-banner">{error}</div>}
        {profileMsg && <div className="success-banner">{profileMsg}</div>}
        {passwordMsg && <div className="success-banner">{passwordMsg}</div>}

        <section className="settings-grid">
          <article className="settings-panel">
            <h2>Account</h2>
            <dl className="settings-dl">
              <div>
                <dt>Username</dt>
                <dd>{user?.username}</dd>
              </div>
              <div>
                <dt>Email</dt>
                <dd>{user?.email}</dd>
              </div>
            </dl>
            <form className="form-stack password-form" onSubmit={(e) => void onSaveProfile(e)}>
              <h3>Display name</h3>
              <label>
                How you appear to collaborators
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  required
                  maxLength={120}
                />
              </label>
              <button className="btn btn-primary" type="submit" disabled={profileBusy}>
                {profileBusy ? 'Saving…' : 'Save display name'}
              </button>
            </form>
            <form className="form-stack password-form" onSubmit={(e) => void onChangePassword(e)}>
              <h3>Change password</h3>
              <label>
                Current password
                <input
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  required
                  minLength={8}
                />
              </label>
              <label>
                New password
                <input
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                  minLength={8}
                />
              </label>
              <button className="btn btn-primary" type="submit" disabled={passwordBusy}>
                {passwordBusy ? 'Updating…' : 'Update password'}
              </button>
            </form>
            <p className="muted settings-note">
              Accounts are local to this installation. There is no cloud login.
            </p>
          </article>

          <article className="settings-panel">
            <h2>Install health</h2>
            <dl className="settings-dl">
              <div>
                <dt>Service</dt>
                <dd>
                  <span className={`status-pill ${health?.status === 'UP' ? 'is-up' : 'is-down'}`}>
                    {health?.status ?? '…'}
                  </span>
                </dd>
              </div>
              <div>
                <dt>Database</dt>
                <dd>
                  <span className={`status-pill ${ready?.database === 'UP' ? 'is-up' : 'is-down'}`}>
                    {ready?.database ?? '…'}
                  </span>
                </dd>
              </div>
              <div>
                <dt>Version</dt>
                <dd>{health?.version ?? ready?.version ?? '—'}</dd>
              </div>
            </dl>
            <p className="muted settings-note">
              Orchestrators can probe <code>/api/v1/health</code> and <code>/api/v1/health/ready</code>.
            </p>
          </article>

          <article className="settings-panel">
            <h2>Rate limits</h2>
            <dl className="settings-dl">
              <div>
                <dt>Auth / minute</dt>
                <dd>
                  {limits
                    ? `${limits.authUsed} used · ${limits.authRemaining} left of ${limits.authPerMinute}`
                    : '—'}
                </dd>
              </div>
              <div>
                <dt>AI refine / minute</dt>
                <dd>
                  {limits
                    ? `${limits.aiUsed} used · ${limits.aiRemaining} left of ${limits.aiPerMinute}`
                    : '—'}
                </dd>
              </div>
              <div>
                <dt>Window</dt>
                <dd>{limits ? `${limits.windowSeconds}s · ${limits.storage}` : '—'}</dd>
              </div>
            </dl>
            <p className="muted settings-note">{limits?.note ?? 'Loading limit status…'}</p>
          </article>

          <article className="settings-panel settings-panel-wide">
            <h2>Local AI (Ollama)</h2>
            <div className="ai-status-block">
              <AiStatusChip />
              <p className="muted">{ai?.message ?? 'Checking Ollama…'}</p>
            </div>
            <dl className="settings-dl">
              <div>
                <dt>Model</dt>
                <dd>{ai?.model ?? '—'}</dd>
              </div>
              <div>
                <dt>Base URL</dt>
                <dd>
                  <code>{ai?.baseUrl ?? '—'}</code>
                </dd>
              </div>
              <div>
                <dt>Concurrency</dt>
                <dd>{ai ? `${ai.inFlight} in flight / max ${ai.maxConcurrent}` : '—'}</dd>
              </div>
              <div>
                <dt>Models seen</dt>
                <dd>{ai?.models?.length ? ai.models.join(', ') : 'none detected'}</dd>
              </div>
              <div>
                <dt>Refine attempts</dt>
                <dd>{ai?.metricsAttempts ?? 0}</dd>
              </div>
              <div>
                <dt>Succeeded / failed</dt>
                <dd>
                  {ai ? `${ai.metricsSucceeded} / ${ai.metricsFailed}` : '—'}
                </dd>
              </div>
              <div>
                <dt>Avg refine latency</dt>
                <dd>{ai ? `${ai.metricsAverageLatencyMs} ms` : '—'}</dd>
              </div>
            </dl>
            {ai && !ai.available && (
              <div className="setup-hint">
                <h3>Setup steps</h3>
                <ol>
                  <li>
                    Install Ollama from{' '}
                    <a href="https://ollama.com" target="_blank" rel="noreferrer">
                      ollama.com
                    </a>
                    .
                  </li>
                  <li>
                    Start with AI: <code>docker compose --profile ai up --build</code>
                  </li>
                  <li>
                    Pull the default model:{' '}
                    <code>docker compose --profile ai exec ollama ollama pull llama3.2:1b</code>
                  </li>
                  <li>
                    Or set <code>OLLAMA_BASE_URL</code> / <code>OLLAMA_MODEL</code> in <code>.env</code>.
                  </li>
                </ol>
                {ai.setupHint && <p className="muted">{ai.setupHint}</p>}
                <p className="muted">
                  Notes keep working while AI is offline. On ~4&nbsp;GB RAM hosts, keep concurrency at 1 and prefer
                  small models such as <code>llama3.2:1b</code>.
                </p>
              </div>
            )}
          </article>

          <article className="settings-panel settings-panel-wide">
            <h2>Backup & docs</h2>
            <p className="muted">
              JSON/Markdown book backup lives in the app. For full host recovery, also dump Postgres and keep the
              covers volume — see <code>SETUP.md</code> and the acceptance checklist.
            </p>
            <div className="settings-actions">
              <Link className="btn" to="/backup">
                Open Backup
              </Link>
              <Link className="btn btn-ghost" to="/library">
                Back to library
              </Link>
            </div>
          </article>
        </section>
      </main>
    </div>
  )
}
