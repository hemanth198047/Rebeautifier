import { useEffect, useState } from 'react'
import {
  api,
  ApiError,
  formatTimestamp,
  type AiStatus,
  type Note,
  type NoteRevision,
  type RefineMode,
  type RevisionStatus,
} from '../api'
import { RichTextEditor } from './RichTextEditor'

const MODES: { mode: RefineMode; label: string; hint: string }[] = [
  { mode: 'GRAMMAR', label: 'Grammar', hint: 'Polish grammar and readability' },
  { mode: 'ORGANIZE', label: 'Organize', hint: 'Headings and bullets' },
  { mode: 'SUMMARY', label: 'Summary', hint: 'Short overview' },
  { mode: 'TAKEAWAYS', label: 'Takeaways', hint: 'Key points list' },
  { mode: 'QA', label: 'Ask', hint: 'Question from this note' },
]

type Props = {
  noteId: string
  canEdit: boolean
  noteBodyRich: string
  contentVersion: number
  noteDirty: boolean
  onEnsureSaved: () => Promise<void>
  onNoteUpdated: (note: Note) => void
}

type Filter = 'ALL' | RevisionStatus

function durationLabel(revision: NoteRevision): string | null {
  if (!revision.finalizedAt) return null
  const ms = new Date(revision.finalizedAt).getTime() - new Date(revision.createdAt).getTime()
  if (!Number.isFinite(ms) || ms < 0) return null
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

export function RefinePanel({
  noteId,
  canEdit,
  noteBodyRich,
  contentVersion,
  noteDirty,
  onEnsureSaved,
  onNoteUpdated,
}: Props) {
  const [aiStatus, setAiStatus] = useState<AiStatus | null>(null)
  const [revisions, setRevisions] = useState<NoteRevision[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [mode, setMode] = useState<RefineMode>('GRAMMAR')
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [phase, setPhase] = useState<'idle' | 'saving' | 'refining' | 'applying'>('idle')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [editBuffer, setEditBuffer] = useState('')
  const [filter, setFilter] = useState<Filter>('ALL')

  const selected = revisions.find((r) => r.id === selectedId) ?? null
  const filtered = revisions.filter((r) => (filter === 'ALL' ? true : r.status === filter))
  const isStale = !!selected && (selected.status === 'STALE' || selected.staleRelativeToNote)
  const canActOnSelected =
    !!selected &&
    canEdit &&
    (selected.status === 'SUCCEEDED' || selected.status === 'STALE' || selected.status === 'FAILED')

  async function loadStatus() {
    try {
      const status = await api<AiStatus>('/api/v1/system/ai-status')
      setAiStatus(status)
    } catch {
      setAiStatus(null)
    }
  }

  async function loadRevisions(preferId?: string | null) {
    try {
      const list = await api<NoteRevision[]>(`/api/v1/notes/${noteId}/refinements`)
      setRevisions(list)
      setSelectedId((prev) => {
        const want = preferId ?? prev
        if (want && list.some((r) => r.id === want)) return want
        return list[0]?.id ?? null
      })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load refinements')
    }
  }

  useEffect(() => {
    void loadStatus()
    void loadRevisions()
  }, [noteId])

  useEffect(() => {
    if (selected?.resultRich) {
      setEditBuffer(selected.resultRich)
    } else {
      setEditBuffer('')
    }
  }, [selectedId, selected?.resultRich, selected?.status])

  function flashSuccess(message: string) {
    setSuccess(message)
    window.setTimeout(() => setSuccess(null), 2800)
  }

  async function runRefine() {
    if (!canEdit) return
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      if (noteDirty) {
        setPhase('saving')
        await onEnsureSaved()
      }
      setPhase('refining')
      const created = await api<NoteRevision>(`/api/v1/notes/${noteId}/refinements`, {
        method: 'POST',
        body: JSON.stringify({
          mode,
          question: mode === 'QA' ? question : undefined,
        }),
      })
      await loadRevisions(created.id)
      await loadStatus()
      flashSuccess(`${created.mode} refinement ready`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Refine failed')
      await loadStatus()
      await loadRevisions()
    } finally {
      setBusy(false)
      setPhase('idle')
    }
  }

  async function accept() {
    if (!selected || !canEdit) return
    if (isStale) {
      setError('This refinement is stale. Regenerate it against the current note before accepting.')
      return
    }
    if (!window.confirm('Accept this refinement into the working note? The original capture stays preserved.')) {
      return
    }
    setBusy(true)
    setPhase('applying')
    setError(null)
    try {
      const note = await api<Note>(`/api/v1/refinements/${selected.id}/accept`, {
        method: 'POST',
        body: JSON.stringify({ editedBodyRich: editBuffer }),
      })
      onNoteUpdated(note)
      await loadRevisions(selected.id)
      flashSuccess('Refinement accepted into the note')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Accept failed')
      await loadRevisions()
    } finally {
      setBusy(false)
      setPhase('idle')
    }
  }

  async function reject() {
    if (!selected || !canEdit) return
    setBusy(true)
    setError(null)
    try {
      await api(`/api/v1/refinements/${selected.id}/reject`, { method: 'POST', body: '{}' })
      await loadRevisions(selected.id)
      flashSuccess('Refinement rejected')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reject failed')
    } finally {
      setBusy(false)
    }
  }

  async function regenerate() {
    if (!selected || !canEdit) return
    setBusy(true)
    setPhase('refining')
    setError(null)
    try {
      if (noteDirty) {
        setPhase('saving')
        await onEnsureSaved()
        setPhase('refining')
      }
      const created = await api<NoteRevision>(`/api/v1/refinements/${selected.id}/regenerate`, {
        method: 'POST',
        body: '{}',
      })
      await loadRevisions(created.id)
      await loadStatus()
      flashSuccess('New refinement generated')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Regenerate failed')
      await loadRevisions()
    } finally {
      setBusy(false)
      setPhase('idle')
    }
  }

  function resetEditBuffer() {
    if (selected?.resultRich) setEditBuffer(selected.resultRich)
  }

  const aiOffline = aiStatus && !aiStatus.available
  const needsModel = !!aiStatus?.enabled && !!aiStatus?.reachable && !aiStatus?.modelAvailable
  const busyLabel =
    phase === 'saving' ? 'Saving note…' : phase === 'refining' ? 'Refining…' : phase === 'applying' ? 'Applying…' : 'Working…'

  return (
    <section className="detail-panel refine-panel">
      <div className="panel-heading">
        <h2>AI review</h2>
        <p className="muted panel-sub">
          Compare, edit, accept, reject, or regenerate. Original capture stays intact until you accept into the
          working note.
        </p>
      </div>

      <div className={`ai-status-banner ${aiStatus?.available ? 'is-online' : 'is-offline'}`}>
        {aiStatus ? (
          <>
            <strong>
              {aiStatus.available
                ? 'AI online'
                : aiStatus.enabled === false
                  ? 'AI disabled — notes still work'
                  : needsModel
                    ? 'AI needs a model — notes still work'
                    : 'AI offline — notes still work'}
            </strong>
            <span>{aiStatus.message}</span>
            <span className="muted">
              Model {aiStatus.model} · queue {aiStatus.inFlight}/{aiStatus.maxConcurrent}
            </span>
            {aiOffline && <p className="ai-setup-hint">{aiStatus.setupHint}</p>}
          </>
        ) : (
          <span>Checking Ollama status…</span>
        )}
        <button className="btn btn-ghost" type="button" onClick={() => void loadStatus()} disabled={busy}>
          Refresh status
        </button>
      </div>

      {!canEdit && (
        <div className="info-banner">You can read refinements on this book, but only editors can run or accept AI changes.</div>
      )}

      {noteDirty && canEdit && (
        <div className="info-banner">Unsaved edits will be saved automatically before refine or regenerate.</div>
      )}

      {error && <div className="error-banner">{error}</div>}
      {success && <div className="success-banner">{success}</div>}

      {canEdit && (
        <div className="refine-controls">
          <div className="mode-grid">
            {MODES.map((m) => (
              <button
                key={m.mode}
                type="button"
                className={`mode-chip ${mode === m.mode ? 'is-active' : ''}`}
                onClick={() => setMode(m.mode)}
                disabled={busy}
              >
                <strong>{m.label}</strong>
                <span>{m.hint}</span>
              </button>
            ))}
          </div>
          {mode === 'QA' && (
            <label className="qa-field">
              Question
              <input
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                placeholder="What is the main worry in this note?"
                disabled={busy}
              />
            </label>
          )}
          <button
            className="btn btn-primary"
            type="button"
            disabled={busy || !noteBodyRich.trim() || (mode === 'QA' && !question.trim()) || !!aiOffline}
            onClick={() => void runRefine()}
          >
            {busy ? busyLabel : 'Run refine'}
          </button>
        </div>
      )}

      {busy && phase !== 'idle' && (
        <div className="refine-progress" role="status" aria-live="polite">
          <span className="refine-progress-bar" />
          <span>{busyLabel}</span>
        </div>
      )}

      {selected && (selected.status === 'SUCCEEDED' || selected.status === 'STALE' || selected.status === 'ACCEPTED') && (
        <div className="compare-grid">
          {isStale && (
            <div className="stale-banner">
              Stale refinement — note is at version {contentVersion}; this run used version{' '}
              {selected.inputContentVersion}. Regenerate before accepting.
            </div>
          )}
          <div className="compare-col">
            <div className="compare-col-head">
              <h3>Current note</h3>
              <span className="muted">v{contentVersion}</span>
            </div>
            <div
              className="compare-body"
              dangerouslySetInnerHTML={{ __html: noteBodyRich || '<p></p>' }}
            />
          </div>
          <div className="compare-col">
            <div className="compare-col-head">
              <h3>
                Refinement · {selected.mode}
                {selected.question ? ` · Q` : ''}
              </h3>
              {durationLabel(selected) && <span className="muted">{durationLabel(selected)}</span>}
            </div>
            {selected.question && <p className="qa-echo muted">Question: {selected.question}</p>}
            {canEdit && selected.status === 'SUCCEEDED' && !isStale ? (
              <div className="refine-editor-wrap">
                <RichTextEditor
                  value={editBuffer}
                  onChange={setEditBuffer}
                  editable={!busy}
                  placeholder="Edit the refinement before accepting…"
                />
              </div>
            ) : (
              <div
                className="compare-body"
                dangerouslySetInnerHTML={{ __html: selected.resultRich || '<p></p>' }}
              />
            )}
            {canActOnSelected && (
              <div className="refine-actions">
                <button
                  className="btn btn-primary"
                  type="button"
                  disabled={busy || isStale}
                  onClick={() => void accept()}
                >
                  Accept into note
                </button>
                <button className="btn btn-ghost" type="button" disabled={busy} onClick={() => void reject()}>
                  Reject
                </button>
                <button className="btn btn-ghost" type="button" disabled={busy || !!aiOffline} onClick={() => void regenerate()}>
                  Regenerate
                </button>
                {canEdit && selected.status === 'SUCCEEDED' && !isStale && (
                  <button className="btn btn-ghost" type="button" disabled={busy} onClick={resetEditBuffer}>
                    Reset edits
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {selected?.status === 'FAILED' && (
        <div className="detail-panel nested-fail">
          <h3>Refinement failed</h3>
          <p className="muted">{selected.errorMessage || 'Ollama returned an error.'}</p>
          {canEdit && (
            <button className="btn btn-primary" type="button" disabled={busy || !!aiOffline} onClick={() => void regenerate()}>
              Try again
            </button>
          )}
        </div>
      )}

      <div className="revision-history">
        <div className="revision-history-head">
          <h3>Revision history</h3>
          <div className="filter-row">
            {(['ALL', 'SUCCEEDED', 'STALE', 'ACCEPTED', 'REJECTED', 'FAILED'] as Filter[]).map((f) => (
              <button
                key={f}
                type="button"
                className={`filter-chip ${filter === f ? 'is-active' : ''}`}
                onClick={() => setFilter(f)}
              >
                {f === 'ALL' ? 'All' : f.toLowerCase()}
              </button>
            ))}
          </div>
        </div>
        {filtered.length === 0 ? (
          <div className="empty-state">No refinements in this filter yet.</div>
        ) : (
          <ul className="list">
            {filtered.map((r) => (
              <li key={r.id}>
                <button
                  type="button"
                  className={`list-item revision-item ${selectedId === r.id ? 'is-selected' : ''}`}
                  onClick={() => setSelectedId(r.id)}
                >
                  <div className="note-link-top">
                    <strong>{r.mode}</strong>
                    <span className={`status-pill status-${r.status.toLowerCase()}`}>{r.status}</span>
                    {r.staleRelativeToNote && r.status === 'SUCCEEDED' && (
                      <span className="status-pill status-stale">stale?</span>
                    )}
                  </div>
                  <div className="note-meta">
                    <span>{formatTimestamp(r.createdAt)}</span>
                    {durationLabel(r) && <span>{durationLabel(r)}</span>}
                    {r.modelName && <span>{r.modelName}</span>}
                    {r.question && <span>Q: {r.question.slice(0, 48)}</span>}
                    {r.errorMessage && <span>{r.errorMessage}</span>}
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
