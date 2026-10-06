import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  api,
  ApiError,
  type AiProvider,
  type AiPullResult,
  type AiSettings,
  type AiStatus,
  type RateLimitStatus,
  type User,
} from '../api'
import { useAuth } from '../auth'
import { AppShell, PageHeader } from '../components/AppShell'
import { AiStatusChip, notifyAiStatusRefresh } from '../components/AiStatusChip'

type Health = {
  status: string
  service: string
  version?: string
}

type Ready = Health & {
  database: string
}

export function SettingsPage() {
  const { user, setUser } = useAuth()
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
  const [aiJustSaved, setAiJustSaved] = useState(false)
  const [profileJustSaved, setProfileJustSaved] = useState(false)
  const [passwordJustSaved, setPasswordJustSaved] = useState(false)
  const [pullBusy, setPullBusy] = useState(false)
  const [pullProgress, setPullProgress] = useState<string | null>(null)

  const [aiEnabled, setAiEnabled] = useState(false)
  const [aiProvider, setAiProvider] = useState<AiProvider>('OLLAMA')
  const [aiModel, setAiModel] = useState('llama3.2:1b')
  const [aiBaseUrl, setAiBaseUrl] = useState('')
  const [aiApiKey, setAiApiKey] = useState('')
  const [clearApiKey, setClearApiKey] = useState(false)
  const [pullOnSave, setPullOnSave] = useState(false)

  useEffect(() => {
    setDisplayName(user?.displayName ?? '')
  }, [user?.displayName])

  function applyAiSettingsForm(settings: AiSettings) {
    setAiSettings(settings)
    setAiEnabled(settings.enabled)
    setAiProvider(settings.provider)
    // Local Ollama must not keep leftover API model ids (e.g. gpt-4o-mini).
    if (settings.provider === 'OLLAMA' && isCloudApiModel(settings.model)) {
      setAiModel('llama3.2:1b')
    } else {
      setAiModel(settings.model)
    }
    const base = settings.baseUrl ?? ''
    if (settings.provider === 'OLLAMA' && /openai\.com/i.test(base)) {
      setAiBaseUrl('')
    } else {
      setAiBaseUrl(base)
    }
    setAiApiKey('')
    setClearApiKey(false)
  }

  function isCloudApiModel(model: string) {
    return /^(gpt-|chatgpt-|o[0-9]|claude-|gemini-|text-embedding-)/i.test(model.trim())
  }

  function isOllamaLibraryModel(model: string) {
    return /^(llama|mistral|qwen|phi|gemma|tinyllama|orca|neural|codellama|deepseek)/i.test(model.trim())
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
      notifyAiStatusRefresh()
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
      setProfileJustSaved(true)
      window.setTimeout(() => setProfileJustSaved(false), 2800)
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
      setPasswordJustSaved(true)
      window.setTimeout(() => setPasswordJustSaved(false), 2800)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change password')
    } finally {
      setPasswordBusy(false)
    }
  }

  async function onSaveAi(event: FormEvent) {
    event.preventDefault()
    setAiMsg(null)
    setError(null)
    setPullProgress(null)
    const wantPullAfterSave = aiEnabled && aiProvider === 'OLLAMA' && pullOnSave
    // If Local Ollama still has an API model id in the field, swap to the default before save.
    const modelToSave =
      aiProvider === 'OLLAMA' && isCloudApiModel(aiModel) ? 'llama3.2:1b' : aiModel
    if (modelToSave !== aiModel) {
      setAiModel(modelToSave)
    }
    setAiBusy(true)
    try {
      // Never ask the server to pull inside Save — that blocked the request for minutes
      // and made Save look broken when the registry was slow or unreachable.
      const saved = await api<AiSettings>('/api/v1/settings/ai', {
        method: 'PUT',
        body: JSON.stringify({
          enabled: aiEnabled,
          provider: aiProvider,
          model: modelToSave,
          baseUrl: aiBaseUrl.trim() || null,
          apiKey: aiApiKey.trim() || null,
          clearApiKey,
          pullModelOnSave: false,
        }),
      })
      applyAiSettingsForm(saved)
      const aiStatus = await api<AiStatus>('/api/v1/system/ai-status')
      setAi(aiStatus)
      notifyAiStatusRefresh()
      if (saved.enabled) {
        setAiMsg(
          aiProvider === 'OLLAMA'
            ? wantPullAfterSave
              ? 'AI settings saved. Starting model download…'
              : 'AI settings saved. Next: click “Pull model now” if the model is not installed yet, then open a note and use Refine.'
            : 'AI settings saved — refine will use this API key and model.',
        )
      } else {
        setAiMsg('AI disabled. Notes still work.')
      }
      setAiBusy(false)
      setAiJustSaved(true)
      window.setTimeout(() => setAiJustSaved(false), 2800)
      if (wantPullAfterSave && saved.enabled) {
        await runPullModel(modelToSave)
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save AI settings')
      setAiBusy(false)
      setPullProgress(null)
    }
  }

  async function runPullModel(modelToPull: string) {
    setPullBusy(true)
    setError(null)
    setPullProgress(
      `Downloading “${modelToPull}” from the Ollama registry into local Ollama storage (Compose volume “ollama”, or ~/.ollama). This can take several minutes on first pull — keep this page open.`,
    )
    try {
      const result = await api<AiPullResult>('/api/v1/settings/ai/pull-model', {
        method: 'POST',
        body: '{}',
      })
      const aiStatus = await api<AiStatus>('/api/v1/system/ai-status')
      setAi(aiStatus)
      notifyAiStatusRefresh()
      const parts = [result.message, result.storageHint, result.nextStep].filter(Boolean)
      if (aiStatus.available) {
        setAiMsg(parts.join(' '))
      } else {
        // Settings stay saved — show guidance without treating Save as failed.
        setAiMsg(
          parts.join(' ') ||
            aiStatus.message ||
            'Download finished contacting Ollama, but the model is not online yet. Check Model name, then Refresh status.',
        )
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not pull model')
    } finally {
      setPullBusy(false)
      setPullProgress(null)
    }
  }

  async function onPullModel() {
    // Heal leftover API model ids so Local Ollama always pulls an Ollama library model.
    const modelToPull = isCloudApiModel(aiModel) ? 'llama3.2:1b' : aiModel
    if (modelToPull !== aiModel) {
      setAiModel(modelToPull)
      try {
        await api<AiSettings>('/api/v1/settings/ai', {
          method: 'PUT',
          body: JSON.stringify({
            enabled: true,
            provider: 'OLLAMA',
            model: modelToPull,
            baseUrl: aiBaseUrl.trim() || null,
            apiKey: null,
            clearApiKey: false,
            pullModelOnSave: false,
          }),
        })
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not reset Ollama model before pull')
        return
      }
    }
    setAiMsg(null)
    await runPullModel(modelToPull)
  }

  return (
    <AppShell active="settings" compactAi={false}>
      <main className="page page-dense settings-page">
        <PageHeader
          eyebrow="Account"
          title="Settings"
          subtitle="Profile, password, AI provider, rate limits, and install health for this deployment."
          actions={
            <button className="btn btn-ghost" type="button" onClick={() => void refreshStatus()}>
              Refresh status
            </button>
          }
          meta={[
            { label: 'AI', value: ai?.available ? 'Online' : ai ? 'Offline' : '…' },
            { label: 'Database', value: ready?.database ?? '…' },
            { label: 'Version', value: health?.version ?? '…' },
            { label: 'User', value: user?.username ?? '…' },
          ]}
        />

        {error && <div className="error-banner">{error}</div>}
        {profileMsg && <div className="success-banner">{profileMsg}</div>}
        {passwordMsg && <div className="success-banner">{passwordMsg}</div>}
        {aiMsg && <div className="success-banner">{aiMsg}</div>}
        {pullProgress && <div className="info-banner">{pullProgress}</div>}

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
              <button
                className={`btn btn-primary${profileJustSaved ? ' btn-save-ok' : ''}`}
                type="submit"
                disabled={profileBusy}
                aria-live="polite"
              >
                {profileBusy ? 'Saving…' : profileJustSaved ? 'Saved' : 'Save profile'}
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
              <button
                className={`btn btn-primary${passwordJustSaved ? ' btn-save-ok' : ''}`}
                type="submit"
                disabled={passwordBusy}
                aria-live="polite"
              >
                {passwordBusy ? 'Updating…' : passwordJustSaved ? 'Updated' : 'Change password'}
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
              1) Enable AI and Save. 2) For Ollama, click Pull model now and wait for the download. 3) When the status
              chip says AI online, open a note and use Refine. Notes always work with AI off.
            </p>
            <div className="ai-status-block">
              <AiStatusChip statusOverride={ai} />
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
                      // Always use an Ollama library model when Local Ollama is selected.
                      if (!aiModel || isCloudApiModel(aiModel)) {
                        setAiModel('llama3.2:1b')
                      }
                      if (/openai\.com/i.test(aiBaseUrl)) {
                        setAiBaseUrl('')
                      }
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
                      if (!aiModel || aiModel === 'llama3.2:1b' || isOllamaLibraryModel(aiModel)) {
                        setAiModel('gpt-4o-mini')
                      }
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
              <p className="muted settings-note">
                {aiProvider === 'OLLAMA'
                  ? 'Local Ollama uses library models such as llama3.2:1b. Switching to Local Ollama clears API model ids like gpt-4o-mini from this field.'
                  : 'Uses your API key against an OpenAI-compatible /chat/completions endpoint. No Ollama pull is needed.'}
              </p>

              <label>
                Base URL{' '}
                {aiProvider === 'OLLAMA'
                  ? '(optional — Compose default http://ollama:11434; leave blank to auto-detect)'
                  : '(optional — defaults to https://api.openai.com/v1)'}
                <input
                  value={aiBaseUrl}
                  onChange={(e) => setAiBaseUrl(e.target.value)}
                  disabled={!aiEnabled}
                  placeholder={aiProvider === 'OLLAMA' ? 'http://ollama:11434' : 'https://api.openai.com/v1'}
                />
              </label>
              {aiProvider === 'OLLAMA' && (
                <p className="muted settings-note">
                  Docker Compose includes the <code>ollama</code> service. Prefer{' '}
                  <code>http://ollama:11434</code> inside Compose, or{' '}
                  <code>http://host.docker.internal:11434</code> / <code>http://127.0.0.1:11434</code> for a host
                  install. If pull fails with an unresolved-host error, restart with <code>./start.sh</code> (or{' '}
                  <code>start.bat</code>) and try again.
                </p>
              )}

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
                  <span>Also pull model after saving (runs separately — Save itself stays fast)</span>
                </label>
              )}

              <div className="settings-actions">
                <button
                  className={`btn btn-primary${aiJustSaved ? ' btn-save-ok' : ''}`}
                  type="submit"
                  disabled={aiBusy || pullBusy}
                  aria-live="polite"
                >
                  {aiBusy ? 'Saving…' : aiJustSaved ? 'Saved' : 'Save AI settings'}
                </button>
                {aiEnabled && aiProvider === 'OLLAMA' && (
                  <button
                    className="btn btn-ghost"
                    type="button"
                    disabled={pullBusy || aiBusy || !aiSettings?.enabled}
                    title={
                      !aiSettings?.enabled
                        ? 'Save AI settings with Enable AI turned on first'
                        : 'Download the model into Ollama storage'
                    }
                    onClick={() => void onPullModel()}
                  >
                    {pullBusy ? 'Downloading model…' : 'Pull model now'}
                  </button>
                )}
              </div>
              {aiEnabled && aiProvider === 'OLLAMA' && !aiSettings?.enabled && (
                <p className="muted settings-note">
                  Pull is available after a successful Save with Enable AI checked.
                </p>
              )}
              {ai?.enabled && aiProvider === 'OLLAMA' && !ai.available && (
                <p className="muted settings-note">
                  {ai.reachable
                    ? ai.models && ai.models.length > 0
                      ? `Ollama is up. Configured model “${ai.model}” is not installed. Installed: ${ai.models.join(', ')}. Change Model to a listed name (or Pull again), Save, then Refresh status.`
                      : `Ollama is up but no models are installed yet. Click Pull model now for “${ai.model}”, wait until it finishes, then Refresh status.`
                    : 'Ollama is not reachable. Restart with ./start.sh (or start.bat), confirm Base URL, then Refresh status.'}
                </p>
              )}
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
    </AppShell>
  )
}
