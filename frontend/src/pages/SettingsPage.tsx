import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  api,
  ApiError,
  type AiProvider,
  type AiSettings,
  type AiStatus,
  type RateLimitStatus,
  type User,
} from '../api'
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
  const [aiSettings, setAiSettings] = useState<AiSettings | null>(null)
  const [limits, setLimits] = useState<RateLimitStatus | null>(null)
  const [health, setHealth] = useState<Health | null>(null)
  const [ready, setReady] = useState<Ready | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [passwordMsg, setPasswordMsg] = useState<string | null>(null)
  const [profileMsg, setProfileMsg] = useState<string | null>(null)
  const [aiMsg, setAiMsg] = useState<string | null>(null)
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)
  const [profileBusy, setProfileBusy] = useState(false)
  const [aiBusy, setAiBusy] = useState(false)
  const [pullBusy, setPullBusy] = useState(false)

  const [aiEnabled, setAiEnabled] = useState(false)
  const [aiProvider, setAiProvider] = useState<AiProvider>('OLLAMA')
  const [aiModel, setAiModel] = useState('llama3.2:1b')
  const [aiBaseUrl, setAiBaseUrl] = useState('')
  const [aiApiKey, setAiApiKey] = useState('')
  const [clearApiKey, setClearApiKey] = useState(false)
  const [pullOnSave, setPullOnSave] = useState(true)

  useEffect(() => {
    setDisplayName(user?.displayName ?? '')
  }, [user?.displayName])

  function applyAiSettingsForm(settings: AiSettings) {
    setAiSettings(settings)
    setAiEnabled(settings.enabled)
    setAiProvider(settings.provider)
    setAiModel(settings.model)
    setAiBaseUrl(settings.baseUrl ?? '')
    setAiApiKey('')
    setClearApiKey(false)
    setPullOnSave(settings.provider === 'OLLAMA')
  }

  async function refreshStatus() {
    setError(null)
    try {
      const [aiStatus, settings, limitStatus, healthStatus, readyStatus] = await Promise.all([
        api<AiStatus>('/api/v1/system/ai-status'),
        api<AiSettings>('/api/v1/settings/ai'),
        api<RateLimitStatus>('/api/v1/system/rate-limits'),
        fetch('/api/v1/health').then(async (r) => (await r.json()) as Health),
        fetch('/api/v1/health/ready').then(async (r) => (await r.json()) as Ready),
      ])
      setAi(aiStatus)
      applyAiSettingsForm(settings)
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

  async function onSaveAi(event: FormEvent) {
    event.preventDefault()
    setAiBusy(true)
    setAiMsg(null)
    setError(null)
    try {
      const saved = await api<AiSettings>('/api/v1/settings/ai', {
        method: 'PUT',
        body: JSON.stringify({
          enabled: aiEnabled,
          provider: aiProvider,
          model: aiModel,
          baseUrl: aiBaseUrl.trim() || null,
          apiKey: aiApiKey.trim() || null,
          clearApiKey,
          pullModelOnSave: aiEnabled && aiProvider === 'OLLAMA' && pullOnSave,
        }),
      })
      applyAiSettingsForm(saved)
      const aiStatus = await api<AiStatus>('/api/v1/system/ai-status')
      setAi(aiStatus)
      setAiMsg(
        saved.pullMessage
          ? `Saved. ${saved.pullMessage}`
          : saved.enabled
            ? 'AI settings saved — refine will use this configuration.'
            : 'AI disabled. Notes still work.',
      )
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save AI settings')
    } finally {
      setAiBusy(false)
    }
  }

  async function onPullModel() {
    setPullBusy(true)
    setAiMsg(null)
    setError(null)
    try {
      const result = await api<{ success: boolean; message: string; modelAvailable: boolean }>(
        '/api/v1/settings/ai/pull-model',
        { method: 'POST', body: '{}' },
      )
      const aiStatus = await api<AiStatus>('/api/v1/system/ai-status')
      setAi(aiStatus)
      setAiMsg(result.message + (result.modelAvailable ? ' Model is ready.' : ''))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not pull model')
    } finally {
      setPullBusy(false)
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

      <main className="page">
        <div className="library-header">
          <div>
            <p className="muted">Account</p>
            <h1>Settings</h1>
          </div>
          <button className="btn btn-ghost" type="button" onClick={() => void refreshStatus()}>
            Refresh status
          </button>
        </div>

        {error && <div className="error-banner">{error}</div>}
        {profileMsg && <div className="success-banner">{profileMsg}</div>}
        {passwordMsg && <div className="success-banner">{passwordMsg}</div>}
        {aiMsg && <div className="success-banner">{aiMsg}</div>}

        <div className="settings-grid">
          <article className="settings-panel">
            <h2>Profile</h2>
            <form className="form-stack" onSubmit={onSaveProfile}>
              <label>
                Username
                <input value={user?.username ?? ''} disabled />
              </label>
              <label>
                Email
                <input value={user?.email ?? ''} disabled />
              </label>
              <label>
                Display name
                <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required maxLength={120} />
              </label>
              <button className="btn btn-primary" type="submit" disabled={profileBusy}>
                {profileBusy ? 'Saving…' : 'Save profile'}
              </button>
            </form>
          </article>

          <article className="settings-panel">
            <h2>Password</h2>
            <form className="form-stack" onSubmit={onChangePassword}>
              <label>
                Current password
                <input
                  type="password"
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
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                  minLength={8}
                />
              </label>
              <button className="btn btn-primary" type="submit" disabled={passwordBusy}>
                {passwordBusy ? 'Updating…' : 'Change password'}
              </button>
            </form>
          </article>

          <article className="settings-panel">
            <h2>Install health</h2>
            <dl className="settings-dl">
              <div>
                <dt>Service</dt>
                <dd>{health ? `${health.status} · ${health.version ?? ''}` : '—'}</dd>
              </div>
              <div>
                <dt>Database</dt>
                <dd>{ready?.database ?? '—'}</dd>
              </div>
            </dl>
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
            <h2>AI</h2>
            <p className="muted panel-sub">
              Enable or disable AI here. Choose local Ollama (pull the model from this screen) or paste an API key for
              any OpenAI-compatible provider. No CLI steps required for day-to-day use.
            </p>
            <div className="ai-status-block">
              <AiStatusChip />
              <p className="muted">{ai?.message ?? 'Checking AI…'}</p>
            </div>

            <form className="form-stack" onSubmit={onSaveAi}>
              <label className="checkbox-row">
                <input type="checkbox" checked={aiEnabled} onChange={(e) => setAiEnabled(e.target.checked)} />
                <span>Enable AI for my account</span>
              </label>

              <fieldset className="provider-fieldset" disabled={!aiEnabled}>
                <legend>Provider</legend>
                <label className="checkbox-row">
                  <input
                    type="radio"
                    name="ai-provider"
                    checked={aiProvider === 'OLLAMA'}
                    onChange={() => {
                      setAiProvider('OLLAMA')
                      setPullOnSave(true)
                      if (!aiModel) setAiModel('llama3.2:1b')
                    }}
                  />
                  <span>Local Ollama</span>
                </label>
                <label className="checkbox-row">
                  <input
                    type="radio"
                    name="ai-provider"
                    checked={aiProvider === 'OPENAI_COMPATIBLE'}
                    onChange={() => {
                      setAiProvider('OPENAI_COMPATIBLE')
                      setPullOnSave(false)
                      if (aiModel === 'llama3.2:1b') setAiModel('gpt-4o-mini')
                    }}
                  />
                  <span>API key (OpenAI-compatible)</span>
                </label>
              </fieldset>

              <label>
                Model
                <input
                  value={aiModel}
                  onChange={(e) => setAiModel(e.target.value)}
                  required
                  maxLength={200}
                  disabled={!aiEnabled}
                  placeholder={aiProvider === 'OLLAMA' ? 'llama3.2:1b' : 'gpt-4o-mini'}
                />
              </label>

              <label>
                Base URL{' '}
                {aiProvider === 'OLLAMA'
                  ? '(optional — defaults to install Ollama URL)'
                  : '(optional — defaults to https://api.openai.com/v1)'}
                <input
                  value={aiBaseUrl}
                  onChange={(e) => setAiBaseUrl(e.target.value)}
                  disabled={!aiEnabled}
                  placeholder={aiProvider === 'OLLAMA' ? 'http://ollama:11434' : 'https://api.openai.com/v1'}
                />
              </label>

              {aiProvider === 'OPENAI_COMPATIBLE' && (
                <>
                  <label>
                    API key
                    <input
                      type="password"
                      value={aiApiKey}
                      onChange={(e) => {
                        setAiApiKey(e.target.value)
                        setClearApiKey(false)
                      }}
                      disabled={!aiEnabled}
                      placeholder={aiSettings?.apiKeyConfigured ? `Saved ${aiSettings.apiKeyHint}` : 'sk-…'}
                      autoComplete="off"
                    />
                  </label>
                  {aiSettings?.apiKeyConfigured && (
                    <label className="checkbox-row">
                      <input
                        type="checkbox"
                        checked={clearApiKey}
                        onChange={(e) => setClearApiKey(e.target.checked)}
                        disabled={!aiEnabled}
                      />
                      <span>Clear saved API key</span>
                    </label>
                  )}
                </>
              )}

              {aiProvider === 'OLLAMA' && (
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={pullOnSave}
                    onChange={(e) => setPullOnSave(e.target.checked)}
                    disabled={!aiEnabled}
                  />
                  <span>Pull model when saving (if Ollama is reachable)</span>
                </label>
              )}

              <div className="settings-actions">
                <button className="btn btn-primary" type="submit" disabled={aiBusy}>
                  {aiBusy ? 'Saving…' : 'Save AI settings'}
                </button>
                {aiEnabled && aiProvider === 'OLLAMA' && (
                  <button
                    className="btn btn-ghost"
                    type="button"
                    disabled={pullBusy || aiBusy}
                    onClick={() => void onPullModel()}
                  >
                    {pullBusy ? 'Pulling model…' : 'Pull model now'}
                  </button>
                )}
              </div>
            </form>

            <dl className="settings-dl" style={{ marginTop: '1.25rem' }}>
              <div>
                <dt>Provider</dt>
                <dd>{ai?.provider ?? aiSettings?.provider ?? '—'}</dd>
              </div>
              <div>
                <dt>Active model</dt>
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
                <dt>Refine attempts</dt>
                <dd>{ai?.metricsAttempts ?? 0}</dd>
              </div>
              <div>
                <dt>Succeeded / failed</dt>
                <dd>{ai ? `${ai.metricsSucceeded} / ${ai.metricsFailed}` : '—'}</dd>
              </div>
            </dl>
            {ai?.setupHint && !ai.available && <p className="muted settings-note">{ai.setupHint}</p>}
          </article>

          <article className="settings-panel settings-panel-wide">
            <h2>Backup & docs</h2>
            <p className="muted">
              JSON/Markdown book backup lives in the app. For full host recovery, also dump Postgres and keep the
              covers volume — see <code>SETUP.md</code> and the acceptance checklist.
            </p>
            <div className="settings-actions">
              <Link className="btn btn-secondary" to="/backup">
                Open backup
              </Link>
            </div>
          </article>
        </div>
      </main>
    </div>
  )
}
