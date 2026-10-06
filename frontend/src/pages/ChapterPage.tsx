import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError, type Book, type Chapter, type Section } from '../api'
import { useAuth } from '../auth'

export function ChapterPage() {
  const { bookId = '', chapterId = '' } = useParams()
  const { user, logout } = useAuth()
  const [book, setBook] = useState<Book | null>(null)
  const [chapter, setChapter] = useState<Chapter | null>(null)
  const [sections, setSections] = useState<Section[]>([])
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const canEdit = book?.myRole === 'OWNER' || book?.myRole === 'EDITOR'

  async function load() {
    try {
      const [bookData, chapters, sectionData] = await Promise.all([
        api<Book>(`/api/v1/books/${bookId}`),
        api<Chapter[]>(`/api/v1/books/${bookId}/chapters`),
        api<Section[]>(`/api/v1/chapters/${chapterId}/sections`),
      ])
      setBook(bookData)
      setChapter(chapters.find((c) => c.id === chapterId) ?? null)
      setSections(sectionData)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load chapter')
    }
  }

  useEffect(() => {
    void load()
  }, [bookId, chapterId])

  async function onCreateSection(event: FormEvent) {
    event.preventDefault()
    try {
      await api(`/api/v1/chapters/${chapterId}/sections`, {
        method: 'POST',
        body: JSON.stringify({ title }),
      })
      setTitle('')
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create section')
    }
  }

  async function moveSection(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= sections.length) return
    const ordered = sections.map((s) => s.id)
    ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
    try {
      const updated = await api<Section[]>(`/api/v1/chapters/${chapterId}/sections/reorder`, {
        method: 'PUT',
        body: JSON.stringify({ orderedIds: ordered }),
      })
      setSections(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reorder failed')
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
          <Link to={`/books/${bookId}`}>{book?.title ?? 'Book'}</Link>
          <span>/</span>
          <span>{chapter?.title ?? 'Chapter'}</span>
        </nav>

        {error && <div className="error-banner">{error}</div>}

        <section className="detail-panel">
          <h1>{chapter?.title ?? 'Chapter'}</h1>
          <p className="muted">Sections nest beneath this chapter. Notes arrive in Phase 2.</p>
        </section>

        <section className="detail-panel">
          <h2>Sections</h2>
          {sections.length === 0 ? (
            <div className="empty-state">No sections yet.</div>
          ) : (
            <ul className="list">
              {sections.map((section, index) => (
                <li key={section.id} className="list-item">
                  <Link to={`/books/${bookId}/chapters/${chapterId}/sections/${section.id}`}>
                    <strong>{section.title}</strong>
                  </Link>
                  {canEdit && (
                    <div style={{ display: 'flex', gap: '0.4rem' }}>
                      <button className="btn btn-ghost" type="button" onClick={() => void moveSection(index, -1)}>
                        ↑
                      </button>
                      <button className="btn btn-ghost" type="button" onClick={() => void moveSection(index, 1)}>
                        ↓
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}

          {canEdit && (
            <form className="inline-form" onSubmit={onCreateSection}>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="New section title"
                required
              />
              <button className="btn btn-primary" type="submit">
                Add section
              </button>
            </form>
          )}
        </section>
      </main>
    </div>
  )
}
