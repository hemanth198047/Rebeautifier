import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  api,
  ApiError,
  formatPageRange,
  formatTimestamp,
  noteHref,
  type Note,
  type NoteAttachType,
  type NoteStatus,
} from '../api'

type Props = {
  bookId: string
  attachType: NoteAttachType
  chapterId?: string
  sectionId?: string
  canEdit: boolean
  notes: Note[]
  onChanged: () => Promise<void> | void
}

export function NotesPanel({
  bookId,
  attachType,
  chapterId,
  sectionId,
  canEdit,
  notes,
  onChanged,
}: Props) {
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [dragId, setDragId] = useState<string | null>(null)

  const scoped = notes.filter((n) => {
    if (n.attachType !== attachType) return false
    if (attachType === 'CHAPTER') return n.chapterId === chapterId
    if (attachType === 'SECTION') return n.sectionId === sectionId
    return true
  })

  async function createNote(event: FormEvent, status: NoteStatus) {
    event.preventDefault()
    setCreating(true)
    setError(null)
    try {
      const created = await api<Note>(`/api/v1/books/${bookId}/notes`, {
        method: 'POST',
        body: JSON.stringify({
          attachType,
          chapterId: attachType === 'BOOK' ? null : chapterId ?? null,
          sectionId: attachType === 'SECTION' ? sectionId ?? null : null,
          title: title.trim() || 'Untitled note',
          bodyRich: '<p></p>',
          status,
          tags: [],
        }),
      })
      setTitle('')
      await onChanged()
      window.location.assign(noteHref(created))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create note')
      setCreating(false)
    }
  }

  async function onDropReorder(targetId: string) {
    if (!canEdit || !dragId || dragId === targetId) {
      setDragId(null)
      return
    }
    const ordered = scoped.map((n) => n.id)
    const from = ordered.indexOf(dragId)
    const to = ordered.indexOf(targetId)
    if (from < 0 || to < 0) {
      setDragId(null)
      return
    }
    ordered.splice(from, 1)
    ordered.splice(to, 0, dragId)
    setDragId(null)
    try {
      await api('/api/v1/notes/reorder', {
        method: 'PUT',
        body: JSON.stringify({
          bookId,
          attachType,
          chapterId: chapterId ?? null,
          sectionId: sectionId ?? null,
          orderedIds: ordered,
        }),
      })
      await onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reorder failed')
    }
  }

  return (
    <section className="detail-panel notes-panel">
      <div className="panel-heading">
        <h2>Notes</h2>
        <p className="muted panel-sub">
          Original text is preserved on first save. AI refine arrives later — notes work offline from Ollama.
        </p>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {scoped.length === 0 ? (
        <div className="empty-state">No notes here yet. Capture a thought when you are ready.</div>
      ) : (
        <ul className="list note-list">
          {scoped.map((note) => {
            const pages = formatPageRange(note.pageStart, note.pageEnd)
            return (
              <li
                key={note.id}
                className={`list-item note-item ${dragId === note.id ? 'is-dragging' : ''}`}
                draggable={canEdit}
                onDragStart={() => setDragId(note.id)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => void onDropReorder(note.id)}
              >
                <Link to={noteHref(note)} className="note-link">
                  <div className="note-link-top">
                    <strong>{note.title || 'Untitled note'}</strong>
                    <span className={`status-pill status-${note.status.toLowerCase()}`}>{note.status}</span>
                  </div>
                  <p className="note-excerpt">
                    {note.bodyPlain?.slice(0, 140) || 'Empty draft'}
                    {(note.bodyPlain?.length ?? 0) > 140 ? '…' : ''}
                  </p>
                  <div className="note-meta">
                    <span>Updated {formatTimestamp(note.updatedAt)}</span>
                    {pages && <span>{pages}</span>}
                    {note.tags.length > 0 && <span>{note.tags.join(' · ')}</span>}
                  </div>
                </Link>
              </li>
            )
          })}
        </ul>
      )}

      {canEdit && (
        <form className="inline-form note-create-form" onSubmit={(e) => void createNote(e, 'DRAFT')}>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="New note title"
            maxLength={200}
          />
          <button className="btn btn-primary" type="submit" disabled={creating}>
            New draft
          </button>
          <button
            className="btn btn-ghost"
            type="button"
            disabled={creating}
            onClick={(e) => void createNote(e as unknown as FormEvent, 'SAVED')}
          >
            New saved note
          </button>
        </form>
      )}
    </section>
  )
}
