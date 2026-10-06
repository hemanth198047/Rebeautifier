import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useBlocker, useNavigate, useParams } from 'react-router-dom'
import {
  api,
  ApiError,
  formatPageRange,
  formatTimestamp,
  type Book,
  type Note,
  type NoteStatus,
} from '../api'
import { useAuth } from '../auth'
import { RichTextEditor } from '../components/RichTextEditor'
import { RefinePanel } from '../components/RefinePanel'

type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'failed'

export function NotePage() {
  const { bookId = '', noteId = '' } = useParams()
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const [book, setBook] = useState<Book | null>(null)
  const [note, setNote] = useState<Note | null>(null)
  const [title, setTitle] = useState('')
  const [bodyRich, setBodyRich] = useState('')
  const [status, setStatus] = useState<NoteStatus>('DRAFT')
  const [pageStart, setPageStart] = useState('')
  const [pageEnd, setPageEnd] = useState('')
  const [tagsInput, setTagsInput] = useState('')
  const [contentVersion, setContentVersion] = useState(1)
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [error, setError] = useState<string | null>(null)
  const [showOriginal, setShowOriginal] = useState(false)

  const dirtyRef = useRef(false)
  const versionRef = useRef(1)
  const autosaveTimer = useRef<number | null>(null)
  const latestPayload = useRef({
    title: '',
    bodyRich: '',
    status: 'DRAFT' as NoteStatus,
    pageStart: '',
    pageEnd: '',
    tagsInput: '',
  })

  const canEdit = book?.myRole === 'OWNER' || book?.myRole === 'EDITOR'

  const applyNote = useCallback((n: Note) => {
    setNote(n)
    setTitle(n.title ?? '')
    setBodyRich(n.bodyRich ?? '')
    setStatus(n.status)
    setPageStart(n.pageStart != null ? String(n.pageStart) : '')
    setPageEnd(n.pageEnd != null ? String(n.pageEnd) : '')
    setTagsInput(n.tags.join(', '))
    setContentVersion(n.contentVersion)
    versionRef.current = n.contentVersion
    dirtyRef.current = false
    setSaveState('idle')
  }, [])

  useEffect(() => {
    async function load() {
      try {
        const [bookData, noteData] = await Promise.all([
          api<Book>(`/api/v1/books/${bookId}`),
          api<Note>(`/api/v1/notes/${noteId}`),
        ])
        if (noteData.bookId !== bookId) {
          setError('Note does not belong to this book')
          return
        }
        setBook(bookData)
        applyNote(noteData)
        setError(null)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Failed to load note')
      }
    }
    void load()
  }, [bookId, noteId, applyNote])

  useEffect(() => {
    latestPayload.current = { title, bodyRich, status, pageStart, pageEnd, tagsInput }
  }, [title, bodyRich, status, pageStart, pageEnd, tagsInput])

  const save = useCallback(
    async (opts?: { statusOverride?: NoteStatus; manual?: boolean }) => {
      if (!canEdit || !note) return
      const payload = latestPayload.current
      const nextStatus = opts?.statusOverride ?? payload.status
      setSaveState('saving')
      setError(null)
      try {
        const tags = payload.tagsInput
          .split(',')
          .map((t) => t.trim())
          .filter(Boolean)
        const clearPages = payload.pageStart.trim() === '' && payload.pageEnd.trim() === ''
        const updated = await api<Note>(`/api/v1/notes/${noteId}`, {
          method: 'PATCH',
          body: JSON.stringify({
            expectedContentVersion: versionRef.current,
            title: payload.title,
            bodyRich: payload.bodyRich,
            status: nextStatus,
            pageStart: payload.pageStart.trim() ? Number(payload.pageStart) : null,
            pageEnd: payload.pageEnd.trim() ? Number(payload.pageEnd) : null,
            clearPageRange: clearPages,
            tags,
          }),
        })
        applyNote(updated)
        setSaveState('saved')
        if (opts?.manual) {
          window.setTimeout(() => {
            if (!dirtyRef.current) setSaveState('idle')
          }, 1600)
        }
      } catch (err) {
        dirtyRef.current = true
        setSaveState('failed')
        if (err instanceof ApiError && err.code === 'VERSION_CONFLICT') {
          setError(err.message)
        } else {
          setError(err instanceof ApiError ? err.message : 'Save failed')
        }
      }
    },
    [applyNote, canEdit, note, noteId],
  )

  const markDirty = useCallback(() => {
    if (!canEdit) return
    dirtyRef.current = true
    setSaveState('dirty')
    if (autosaveTimer.current) window.clearTimeout(autosaveTimer.current)
    autosaveTimer.current = window.setTimeout(() => {
      void save()
    }, 1200)
  }, [canEdit, save])

  useEffect(() => {
    return () => {
      if (autosaveTimer.current) window.clearTimeout(autosaveTimer.current)
    }
  }, [])

  useEffect(() => {
    function onBeforeUnload(event: BeforeUnloadEvent) {
      if (dirtyRef.current || saveState === 'saving') {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [saveState])

  const blocker = useBlocker(dirtyRef.current || saveState === 'saving' || saveState === 'dirty')
  useEffect(() => {
    if (blocker.state !== 'blocked') return
    const leave = window.confirm('You have unsaved changes. Leave without saving?')
    if (leave) blocker.proceed()
    else blocker.reset()
  }, [blocker])

  async function onManualSave(event?: FormEvent) {
    event?.preventDefault()
    if (autosaveTimer.current) window.clearTimeout(autosaveTimer.current)
    await save({ statusOverride: 'SAVED', manual: true })
  }

  async function deleteNote() {
    if (!canEdit || !note) return
    if (!confirm('Delete this note? Original and working content will be removed.')) return
    try {
      await api(`/api/v1/notes/${noteId}`, { method: 'DELETE' })
      dirtyRef.current = false
      navigate(backHref())
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Delete failed')
    }
  }

  function backHref(): string {
    if (!note) return `/books/${bookId}`
    if (note.attachType === 'SECTION' && note.chapterId && note.sectionId) {
      return `/books/${bookId}/chapters/${note.chapterId}/sections/${note.sectionId}`
    }
    if (note.attachType === 'CHAPTER' && note.chapterId) {
      return `/books/${bookId}/chapters/${note.chapterId}`
    }
    return `/books/${bookId}`
  }

  const saveLabel =
    saveState === 'saving'
      ? 'Saving…'
      : saveState === 'saved'
        ? 'Saved'
        : saveState === 'failed'
          ? 'Save failed'
          : saveState === 'dirty'
            ? 'Unsaved changes'
            : status === 'DRAFT'
              ? 'Draft'
              : 'Up to date'

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/library" className="brand">
          Rebeautifier<span>.</span>
        </Link>
        <div className="topbar-actions">
          <span className={`save-indicator save-${saveState}`}>{saveLabel}</span>
          <span className="muted">{user?.displayName}</span>
          <button className="btn btn-ghost" type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="page note-page">
        <nav className="breadcrumb">
          <Link to="/library">Library</Link>
          <span>/</span>
          <Link to={`/books/${bookId}`}>{book?.title ?? 'Book'}</Link>
          <span>/</span>
          <Link to={backHref()}>Notes</Link>
          <span>/</span>
          <span>{title || 'Note'}</span>
        </nav>

        {error && <div className="error-banner">{error}</div>}

        <section className="detail-panel note-editor-panel">
          <div className="note-editor-header">
            <input
              className="note-title-input"
              value={title}
              onChange={(e) => {
                setTitle(e.target.value)
                markDirty()
              }}
              placeholder="Note title"
              disabled={!canEdit}
              maxLength={200}
            />
            <div className="note-editor-actions">
              {canEdit && (
                <>
                  <button className="btn btn-primary" type="button" onClick={() => void onManualSave()}>
                    Save
                  </button>
                  <button
                    className="btn btn-ghost"
                    type="button"
                    onClick={() => {
                      setStatus('DRAFT')
                      markDirty()
                    }}
                  >
                    Keep as draft
                  </button>
                  <button className="btn btn-danger" type="button" onClick={() => void deleteNote()}>
                    Delete
                  </button>
                </>
              )}
            </div>
          </div>

          <div className="note-meta-bar">
            <label>
              Status
              <select
                value={status}
                disabled={!canEdit}
                onChange={(e) => {
                  setStatus(e.target.value as NoteStatus)
                  markDirty()
                }}
              >
                <option value="DRAFT">Draft</option>
                <option value="SAVED">Saved</option>
              </select>
            </label>
            <label>
              Page start
              <input
                type="number"
                min={1}
                value={pageStart}
                disabled={!canEdit}
                onChange={(e) => {
                  setPageStart(e.target.value)
                  markDirty()
                }}
                placeholder="—"
              />
            </label>
            <label>
              Page end
              <input
                type="number"
                min={1}
                value={pageEnd}
                disabled={!canEdit}
                onChange={(e) => {
                  setPageEnd(e.target.value)
                  markDirty()
                }}
                placeholder="—"
              />
            </label>
            <label className="tags-field">
              Tags
              <input
                value={tagsInput}
                disabled={!canEdit}
                onChange={(e) => {
                  setTagsInput(e.target.value)
                  markDirty()
                }}
                placeholder="morning, idea"
              />
            </label>
          </div>

          <div className="note-timestamps muted">
            <span>Created {note ? formatTimestamp(note.createdAt) : '—'}</span>
            <span>Updated {note ? formatTimestamp(note.updatedAt) : '—'}</span>
            <span>Version {contentVersion}</span>
            {formatPageRange(
              pageStart ? Number(pageStart) : null,
              pageEnd ? Number(pageEnd) : null,
            ) && (
              <span>
                {formatPageRange(
                  pageStart ? Number(pageStart) : null,
                  pageEnd ? Number(pageEnd) : null,
                )}
              </span>
            )}
          </div>

          <RichTextEditor
            value={bodyRich}
            editable={canEdit}
            onChange={(html) => {
              setBodyRich(html)
              markDirty()
            }}
            placeholder="Write freely. Your original is kept when you save."
          />

          {note?.bodyOriginal && (
            <div className="original-panel">
              <button
                className="btn btn-ghost"
                type="button"
                onClick={() => setShowOriginal((v) => !v)}
              >
                {showOriginal ? 'Hide original capture' : 'Show original capture'}
              </button>
              {showOriginal && (
                <div
                  className="original-body"
                  dangerouslySetInnerHTML={{ __html: note.bodyOriginal }}
                />
              )}
            </div>
          )}
        </section>

        {note && (
          <RefinePanel
            noteId={note.id}
            canEdit={!!canEdit}
            noteBodyRich={bodyRich}
            contentVersion={contentVersion}
            onNoteUpdated={(updated) => {
              applyNote(updated)
            }}
          />
        )}
      </main>
    </div>
  )
}
