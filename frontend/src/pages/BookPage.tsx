import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError, formatTimestamp, type Book, type Chapter, type Note } from '../api'
import { useDialog } from '../components/DialogProvider'
import { AppShell } from '../components/AppShell'
import { CoverPickerDialog } from '../components/CoverPickerDialog'
import { NotesPanel } from '../components/NotesPanel'
import { ShareDialog } from '../components/ShareDialog'

export function BookPage() {
  const { bookId = '' } = useParams()
  const navigate = useNavigate()
  const { confirm } = useDialog()
  const [book, setBook] = useState<Book | null>(null)
  const [chapters, setChapters] = useState<Chapter[]>([])
  const [notes, setNotes] = useState<Note[]>([])
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [shareOpen, setShareOpen] = useState(false)
  const [coverPickerOpen, setCoverPickerOpen] = useState(false)
  const [dragChapterId, setDragChapterId] = useState<string | null>(null)
  const canEdit = book?.myRole === 'OWNER' || book?.myRole === 'EDITOR'
  const isOwner = book?.myRole === 'OWNER'

  async function load() {
    try {
      const [bookData, chapterData, noteData] = await Promise.all([
        api<Book>(`/api/v1/books/${bookId}`),
        api<Chapter[]>(`/api/v1/books/${bookId}/chapters`),
        api<Note[]>(`/api/v1/books/${bookId}/notes`),
      ])
      setBook(bookData)
      setChapters(chapterData)
      setNotes(noteData)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load book')
    }
  }

  useEffect(() => {
    void load()
  }, [bookId])

  async function onCreateChapter(event: FormEvent) {
    event.preventDefault()
    try {
      await api(`/api/v1/books/${bookId}/chapters`, {
        method: 'POST',
        body: JSON.stringify({ title }),
      })
      setTitle('')
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create chapter')
    }
  }

  async function onCoverChange(file: File | null) {
    if (!file) return
    const body = new FormData()
    body.append('file', file)
    try {
      await api(`/api/v1/books/${bookId}/cover`, { method: 'PUT', body })
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Cover upload failed')
    }
  }

  async function removeCover() {
    try {
      await api(`/api/v1/books/${bookId}/cover`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove cover')
    }
  }

  async function reorderChapters(orderedIds: string[]) {
    try {
      const updated = await api<Chapter[]>(`/api/v1/books/${bookId}/chapters/reorder`, {
        method: 'PUT',
        body: JSON.stringify({ orderedIds }),
      })
      setChapters(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reorder failed')
    }
  }

  async function moveChapter(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= chapters.length) return
    const ordered = chapters.map((c) => c.id)
    ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
    await reorderChapters(ordered)
  }

  async function onDropChapter(targetId: string) {
    if (!canEdit || !dragChapterId || dragChapterId === targetId) {
      setDragChapterId(null)
      return
    }
    const ordered = chapters.map((c) => c.id)
    const from = ordered.indexOf(dragChapterId)
    const to = ordered.indexOf(targetId)
    setDragChapterId(null)
    if (from < 0 || to < 0) return
    ordered.splice(from, 1)
    ordered.splice(to, 0, dragChapterId)
    await reorderChapters(ordered)
  }

  async function deleteBook() {
    const ok = await confirm({
      title: 'Delete book',
      message: 'Delete this book and its chapters? This cannot be undone.',
      confirmLabel: 'Delete book',
      cancelLabel: 'Cancel',
      danger: true,
    })
    if (!ok) return
    try {
      await api(`/api/v1/books/${bookId}`, { method: 'DELETE' })
      navigate('/library')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Delete failed')
    }
  }

  return (
    <AppShell active="book">
      <main className="page page-dense">
        <nav className="breadcrumb">
          <Link to="/library">Library</Link>
          <span>/</span>
          <span>{book?.title ?? 'Book'}</span>
        </nav>

        {error && <div className="error-banner">{error}</div>}

        {book && (
          <section className="detail-panel book-hero-panel dense-panel">
            <div className="book-hero-layout">
              <div className="cover-stage">
                {book.coverUrl ? (
                  <img className="cover-preview cover-preview-lg" src={book.coverUrl} alt="" />
                ) : (
                  <div className="cover-preview cover-preview-lg cover-empty" aria-hidden>
                    <span>No cover yet</span>
                  </div>
                )}
                {canEdit && (
                  <div className="cover-actions">
                    <button className="btn btn-primary" type="button" onClick={() => setCoverPickerOpen(true)}>
                      Browse free covers
                    </button>
                    <label className="btn btn-ghost">
                      {book.coverUrl ? 'Upload file' : 'Upload cover'}
                      <input
                        type="file"
                        accept="image/png,image/jpeg,image/webp,image/gif"
                        hidden
                        onChange={(e) => void onCoverChange(e.target.files?.[0] ?? null)}
                      />
                    </label>
                    {book.coverUrl && (
                      <button className="btn btn-ghost" type="button" onClick={() => void removeCover()}>
                        Remove
                      </button>
                    )}
                  </div>
                )}
              </div>
              <div className="book-hero-copy">
                <p className="page-eyebrow">Book workspace</p>
                <div className="book-title-row">
                  <h1>{book.title}</h1>
                  <span className={`role-pill role-${book.myRole.toLowerCase()}`}>{book.myRole.toLowerCase()}</span>
                </div>
                <p className="muted">{book.description || 'No description yet.'}</p>
                <dl className="page-meta-strip">
                  <div>
                    <dt>Owner</dt>
                    <dd>{book.ownerDisplayName}</dd>
                  </div>
                  <div>
                    <dt>Chapters</dt>
                    <dd>{chapters.length}</dd>
                  </div>
                  <div>
                    <dt>Notes</dt>
                    <dd>{notes.length}</dd>
                  </div>
                  <div>
                    <dt>Updated</dt>
                    <dd>{formatTimestamp(book.updatedAt)}</dd>
                  </div>
                </dl>
                <div className="book-hero-actions">
                  <Link className="btn btn-ghost" to="/backup">
                    Backup
                  </Link>
                  {isOwner && (
                    <button className="btn btn-primary" type="button" onClick={() => setShareOpen(true)}>
                      Share
                    </button>
                  )}
                  {isOwner && (
                    <button className="btn btn-danger" type="button" onClick={() => void deleteBook()}>
                      Delete book
                    </button>
                  )}
                </div>
              </div>
            </div>
          </section>
        )}

        <div className="workspace-split">
        <section className="detail-panel dense-panel">
          <div className="panel-heading">
            <h2>Chapters</h2>
            {canEdit && <p className="muted panel-sub">Drag to reorder, or use the arrows.</p>}
          </div>
          {chapters.length === 0 ? (
            <div className="empty-state">No chapters yet.</div>
          ) : (
            <ul className="list">
              {chapters.map((chapter, index) => (
                <li
                  key={chapter.id}
                  className={`list-item ${dragChapterId === chapter.id ? 'is-dragging' : ''}`}
                  draggable={canEdit}
                  onDragStart={() => setDragChapterId(chapter.id)}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={() => void onDropChapter(chapter.id)}
                >
                  <Link to={`/books/${bookId}/chapters/${chapter.id}`}>
                    <strong>{chapter.title}</strong>
                  </Link>
                  {canEdit && (
                    <div className="reorder-controls">
                      <button className="btn btn-ghost" type="button" onClick={() => void moveChapter(index, -1)}>
                        ↑
                      </button>
                      <button className="btn btn-ghost" type="button" onClick={() => void moveChapter(index, 1)}>
                        ↓
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}

          {canEdit && (
            <form className="inline-form" onSubmit={onCreateChapter}>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="New chapter title"
                required
              />
              <button className="btn btn-primary" type="submit">
                Add chapter
              </button>
            </form>
          )}
        </section>

        <NotesPanel
          bookId={bookId}
          attachType="BOOK"
          canEdit={!!canEdit}
          notes={notes}
          onChanged={load}
        />
        </div>
      </main>

      {isOwner && (
        <ShareDialog bookId={bookId} open={shareOpen} onClose={() => setShareOpen(false)} />
      )}
      {canEdit && (
        <CoverPickerDialog
          bookId={bookId}
          bookTitle={book?.title}
          open={coverPickerOpen}
          onClose={() => setCoverPickerOpen(false)}
          onApplied={() => void load()}
        />
      )}
    </AppShell>
  )
}
