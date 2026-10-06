import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError, type Book, type Chapter, type Section } from '../api'
import { useAuth } from '../auth'

export function SectionPage() {
  const { bookId = '', chapterId = '', sectionId = '' } = useParams()
  const { user, logout } = useAuth()
  const [book, setBook] = useState<Book | null>(null)
  const [chapter, setChapter] = useState<Chapter | null>(null)
  const [section, setSection] = useState<Section | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    async function load() {
      try {
        const [bookData, chapters, sections] = await Promise.all([
          api<Book>(`/api/v1/books/${bookId}`),
          api<Chapter[]>(`/api/v1/books/${bookId}/chapters`),
          api<Section[]>(`/api/v1/chapters/${chapterId}/sections`),
        ])
        setBook(bookData)
        setChapter(chapters.find((c) => c.id === chapterId) ?? null)
        setSection(sections.find((s) => s.id === sectionId) ?? null)
        setError(null)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Failed to load section')
      }
    }
    void load()
  }, [bookId, chapterId, sectionId])

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
          <Link to={`/books/${bookId}/chapters/${chapterId}`}>{chapter?.title ?? 'Chapter'}</Link>
          <span>/</span>
          <span>{section?.title ?? 'Section'}</span>
        </nav>

        {error && <div className="error-banner">{error}</div>}

        <section className="detail-panel">
          <h1>{section?.title ?? 'Section'}</h1>
          <p className="muted">
            This section is ready for notes. The rich-text note editor arrives in Phase 2 — the hierarchy and
            library already persist.
          </p>
          <div className="empty-state" style={{ marginTop: '1.5rem' }}>
            Notes will live here. Capture, refine, and keep originals untouched.
          </div>
        </section>
      </main>
    </div>
  )
}
