import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError, type Book, type Chapter, type Note, type Section } from '../api'
import { AppShell } from '../components/AppShell'
import { NotesPanel } from '../components/NotesPanel'

export function SectionPage() {
  const { bookId = '', chapterId = '', sectionId = '' } = useParams()
  const [book, setBook] = useState<Book | null>(null)
  const [chapter, setChapter] = useState<Chapter | null>(null)
  const [section, setSection] = useState<Section | null>(null)
  const [notes, setNotes] = useState<Note[]>([])
  const [error, setError] = useState<string | null>(null)
  const canEdit = book?.myRole === 'OWNER' || book?.myRole === 'EDITOR'

  async function load() {
    try {
      const [bookData, chapters, sections, noteData] = await Promise.all([
        api<Book>(`/api/v1/books/${bookId}`),
        api<Chapter[]>(`/api/v1/books/${bookId}/chapters`),
        api<Section[]>(`/api/v1/chapters/${chapterId}/sections`),
        api<Note[]>(`/api/v1/books/${bookId}/notes?sectionId=${sectionId}`),
      ])
      setBook(bookData)
      setChapter(chapters.find((c) => c.id === chapterId) ?? null)
      setSection(sections.find((s) => s.id === sectionId) ?? null)
      setNotes(noteData)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load section')
    }
  }

  useEffect(() => {
    void load()
  }, [bookId, chapterId, sectionId])

  return (
    <AppShell active="book">
      <main className="page page-dense">
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

        <section className="detail-panel dense-panel">
          <p className="page-eyebrow">Section</p>
          <div className="book-title-row">
            <h1>{section?.title ?? 'Section'}</h1>
            {book && (
              <span className={`role-pill role-${book.myRole.toLowerCase()}`}>{book.myRole.toLowerCase()}</span>
            )}
          </div>
          <p className="muted">
            Capture notes for this section. Originals stay intact when you save — no Ollama required.
          </p>
          <dl className="page-meta-strip">
            <div>
              <dt>Notes</dt>
              <dd>{notes.length}</dd>
            </div>
            <div>
              <dt>Chapter</dt>
              <dd>{chapter?.title ?? '—'}</dd>
            </div>
            <div>
              <dt>Book</dt>
              <dd>{book?.title ?? '—'}</dd>
            </div>
          </dl>
        </section>

        <NotesPanel
          bookId={bookId}
          attachType="SECTION"
          chapterId={chapterId}
          sectionId={sectionId}
          canEdit={!!canEdit}
          notes={notes}
          onChanged={load}
        />
      </main>
    </AppShell>
  )
}
