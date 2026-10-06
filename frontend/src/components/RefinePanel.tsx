import { useEffect, useState } from 'react'
import {
  api,
  ApiError,
  formatTimestamp,
  type AiStatus,
  type Note,
  type NoteRevision,
  type RefineMode,
} from '../api'

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
  onNoteUpdated: (note: Note) => void
}

export function RefinePanel({ noteId, canEdit, noteBodyRich, contentVersion, onNoteUpdated }: Props) {
  const [aiStatus, setAiStatus] = useState<AiStatus | null>(null)
  const [revisions, setRevisions] = useState<NoteRevision[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [mode, setMode] = useState<RefineMode>('GRAMMAR')
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editBuffer, setEditBuffer] = useState('')

  const selected = revisions.find((r) => r.id === selectedId) ?? null

  async function loadStatus() {
    try {
      const status = await api<AiStatus>('/api/v1/system/ai-status')
      setAiStatus(status)
    } catch {
      setAiStatus(null)
    }
  }

  async function loadRevisions() {
    try {
      const list = await api<NoteRevision[]>(`/api/v1/notes/${noteId}/refinements`)
      setRevisions(list)
      if (list.length > 0) {
        setSelectedId((prev) => prev && list.some((r) => r.id === prev) ? prev : list[0].id)
      }
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

  async function runRefine() {
    if (!canEdit) return
    setBusy(true)
    setError(null)
    try {
      const created = await api<NoteRevision>(`/api/v1/notes/${noteId}/refinements`, {
        method: 'POST',
        body: JSON.stringify({
          mode,
          question: mode === 'QA' ? question : undefined,
        }),
      })
      await loadRevisions()
      await loadStatus()
      setSelectedId(created.id)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Refine failed')
      await loadStatus()
      await loadRevisions()
    } finally {
      setBusy(false)
    }
  }

  async function accept() {
    if (!selected || !canEdit) return
    setBusy(true)
    setError(null)
    try {
      const note = await api<Note>(`/api/v1/refinements/${selected.id}/accept`, {
        method: 'POST',
        body: JSON.stringify({ editedBodyRich: editBuffer }),
      })
      onNoteUpdated(note)
      await loadRevisions()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Accept failed')
      await loadRevisions()
    } finally {
      setBusy(false)
    }
  }

  async function reject() {
    if (!selected || !canEdit) return
    setBusy(true)
    try {
      await api(`/api/v1/refinements/${selected.id}/reject`, { method: 'POST', body: '{}' })
      await loadRevisions()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reject failed')
    } finally {
      setBusy(false)
    }
  }

  async function regenerate() {
    if (!selected || !canEdit) return
    setBusy(true)
    setError(null)
    try {
      const created = await api<NoteRevision>(`/api/v1/refinements/${selected.id}/regenerate`, {
        method: 'POST',
        body: '{}',
      })
      await loadRevisions()
      setSelectedId(created.id)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Regenerate failed')
      await loadRevisions()
    } finally {
      setBusy(false)
    }
  }

  const aiOffline = aiStatus && !aiStatus.available

  return (
    <section className="detail-panel refine-panel">
      <div className="panel-heading">
        <h2>Refine with AI</h2>
        <p className="muted panel-sub">
          Original note stays untouched until you accept. Works only through the local API → Ollama.
        </p>
      </div>

      <div className={`ai-status-banner ${aiStatus?.available ? 'is-online' : 'is-offline'}`}>
        {aiStatus ? (
          <>
            <strong>{aiStatus.available ? 'AI online' : 'AI offline — notes still work'}</strong>
            <span>{aiStatus.message}</span>
            <span className="muted">
              Model {aiStatus.model} · in flight {aiStatus.inFlight}/{aiStatus.maxConcurrent}
            </span>
            {aiOffline && <p className="ai-setup-hint">{aiStatus.setupHint}</p>}
          </>
        ) : (
          <span>Checking Ollama status…</span>
        )}
        <button className="btn btn-ghost" type="button" onClick={() => void loadStatus()}>
          Refresh status
        </button>
      </div>

      {error && <div className="error-banner">{error}</div>}

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
            disabled={busy || !noteBodyRich.trim() || (mode === 'QA' && !question.trim())}
            onClick={() => void runRefine()}
          >
            {busy ? 'Working…' : 'Run refine'}
          </button>
        </div>
      )}

      {selected && (selected.status === 'SUCCEEDED' || selected.status === 'STALE' || selected.status === 'ACCEPTED') && (
        <div className="compare-grid">
          {(selected.status === 'STALE' || selected.staleRelativeToNote) && (
            <div className="stale-banner">
              This refinement may be stale (note version is now {contentVersion}; refinement used{' '}
              {selected.inputContentVersion}). Regenerate before accepting.
            </div>
          )}
          <div className="compare-col">
            <h3>Current note</h3>
            <div className="compare-body" dangerouslySetInnerHTML={{ __html: noteBodyRich || '<p></p>' }} />
          </div>
          <div className="compare-col">
            <h3>Refinement ({selected.mode})</h3>
            {canEdit && selected.status === 'SUCCEEDED' ? (
              <textarea
                className="refine-edit"
                value={editBuffer}
                onChange={(e) => setEditBuffer(e.target.value)}
                rows={12}
              />
            ) : (
              <div
                className="compare-body"
                dangerouslySetInnerHTML={{ __html: selected.resultRich || '<p></p>' }}
              />
            )}
            {canEdit && (
              <div className="refine-actions">
                <button
                  className="btn btn-primary"
                  type="button"
                  disabled={busy || selected.status === 'STALE' || selected.staleRelativeToNote}
                  onClick={() => void accept()}
                >
                  Accept
                </button>
                <button className="btn btn-ghost" type="button" disabled={busy} onClick={() => void reject()}>
                  Reject
                </button>
                <button className="btn btn-ghost" type="button" disabled={busy} onClick={() => void regenerate()}>
                  Regenerate
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      <div className="revision-history">
        <h3>Revision history</h3>
        {revisions.length === 0 ? (
          <div className="empty-state">No refinements yet.</div>
        ) : (
          <ul className="list">
            {revisions.map((r) => (
              <li key={r.id}>
                <button
                  type="button"
                  className={`list-item revision-item ${selectedId === r.id ? 'is-selected' : ''}`}
                  onClick={() => setSelectedId(r.id)}
                >
                  <div>
                    <strong>{r.mode}</strong>
                    <span className={`status-pill status-${r.status.toLowerCase()}`}>{r.status}</span>
                  </div>
                  <div className="note-meta">
                    <span>{formatTimestamp(r.createdAt)}</span>
                    {r.modelName && <span>{r.modelName}</span>}
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
