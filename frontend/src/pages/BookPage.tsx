import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError, type Book, type Chapter } from '../api'
import { useAuth } from '../auth'

export function BookPage() {
  const { bookId = '' } = useParams()
  const navigate = useNavigate()
  const { logout, user } = useAuth()
  const [book, setBook] = useState<Book | null>(null)
  const [chapters, setChapters] = useState<Chapter[]>([])
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const canEdit = book?.myRole === 'OWNER' || book?.myRole === 'EDITOR'

  async function load() {
    try {
      const [bookData, chapterData] = await Promise.all([
        api<Book>(`/api/v1/books/${bookId}`),
        api<Chapter[]>(`/api/v1/books/${bookId}/chapters`),
      ])
      setBook(bookData)
      setChapters(chapterData)
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

  async function moveChapter(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= chapters.length) return
    const ordered = chapters.map((c) => c.id)
    ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
    try {
      const updated = await api<Chapter[]>(`/api/v1/books/${bookId}/chapters/reorder`, {
        method: 'PUT',
        body: JSON.stringify({ orderedIds: ordered }),
      })
      setChapters(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reorder failed')
    }
  }

  async function deleteBook() {
    if (!confirm('Delete this book and its chapters?')) return
    try {
      await api(`/api/v1/books/${bookId}`, { method: 'DELETE' })
      navigate('/library')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Delete failed')
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/library" className="brand">
          Rebeautifier<span>.</span>
        </Link>
        <div className="topbar-actions">
          <span className="muted">{user?.displayName}</span>
          <button className="btn btn-ghost" type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="page">
        <nav className="breadcrumb">
          <Link to="/library">Library</Link>
          <span>/</span>
          <span>{book?.title ?? 'Book'}</span>
        </nav>

        {error && <div className="error-banner">{error}</div>}

        {book && (
          <section className="detail-panel">
            <h1>{book.title}</h1>
            <p className="muted">{book.description || 'No description yet.'}</p>
            <div className="cover-row">
              {book.coverUrl ? (
                <img className="cover-preview" src={book.coverUrl} alt="" />
              ) : (
                <div className="cover-preview" aria-hidden />
              )}
              {canEdit && (
                <label className="btn btn-ghost">
                  Upload cover
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/webp,image/gif"
                    hidden
                    onChange={(e) => void onCoverChange(e.target.files?.[0] ?? null)}
                  />
                </label>
              )}
              {book.myRole === 'OWNER' && (
                <button className="btn btn-danger" type="button" onClick={() => void deleteBook()}>
                  Delete book
                </button>
              )}
            </div>
          </section>
        )}

        <section className="detail-panel">
          <h2>Chapters</h2>
          {chapters.length === 0 ? (
            <div className="empty-state">No chapters yet.</div>
          ) : (
            <ul className="list">
              {chapters.map((chapter, index) => (
                <li key={chapter.id} className="list-item">
                  <Link to={`/books/${bookId}/chapters/${chapter.id}`}>
                    <strong>{chapter.title}</strong>
                  </Link>
                  {canEdit && (
                    <div style={{ display: 'flex', gap: '0.4rem' }}>
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
      </main>
    </div>
  )
}
