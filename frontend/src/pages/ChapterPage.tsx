import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError, type Book, type Chapter, type Note, type Section } from '../api'
import { useAuth } from '../auth'
import { NotesPanel } from '../components/NotesPanel'

export function ChapterPage() {
  const { bookId = '', chapterId = '' } = useParams()
  const { user, logout } = useAuth()
  const [book, setBook] = useState<Book | null>(null)
  const [chapter, setChapter] = useState<Chapter | null>(null)
  const [sections, setSections] = useState<Section[]>([])
  const [notes, setNotes] = useState<Note[]>([])
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [dragSectionId, setDragSectionId] = useState<string | null>(null)
  const canEdit = book?.myRole === 'OWNER' || book?.myRole === 'EDITOR'

  async function load() {
    try {
      const [bookData, chapters, sectionData, noteData] = await Promise.all([
        api<Book>(`/api/v1/books/${bookId}`),
        api<Chapter[]>(`/api/v1/books/${bookId}/chapters`),
        api<Section[]>(`/api/v1/chapters/${chapterId}/sections`),
        api<Note[]>(`/api/v1/books/${bookId}/notes?chapterId=${chapterId}`),
      ])
      setBook(bookData)
      setChapter(chapters.find((c) => c.id === chapterId) ?? null)
      setSections(sectionData)
      setNotes(noteData)
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

  async function reorderSections(orderedIds: string[]) {
    try {
      const updated = await api<Section[]>(`/api/v1/chapters/${chapterId}/sections/reorder`, {
        method: 'PUT',
        body: JSON.stringify({ orderedIds }),
      })
      setSections(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reorder failed')
    }
  }

  async function moveSection(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= sections.length) return
    const ordered = sections.map((s) => s.id)
    ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
    await reorderSections(ordered)
  }

  async function onDropSection(targetId: string) {
    if (!canEdit || !dragSectionId || dragSectionId === targetId) {
      setDragSectionId(null)
      return
    }
    const ordered = sections.map((s) => s.id)
    const from = ordered.indexOf(dragSectionId)
    const to = ordered.indexOf(targetId)
    setDragSectionId(null)
    if (from < 0 || to < 0) return
    ordered.splice(from, 1)
    ordered.splice(to, 0, dragSectionId)
    await reorderSections(ordered)
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
          <p className="muted">Sections nest beneath this chapter. Capture chapter-level notes below.</p>
        </section>

        <section className="detail-panel">
          <div className="panel-heading">
            <h2>Sections</h2>
            {canEdit && <p className="muted panel-sub">Drag to reorder, or use the arrows.</p>}
          </div>
          {sections.length === 0 ? (
            <div className="empty-state">No sections yet.</div>
          ) : (
            <ul className="list">
              {sections.map((section, index) => (
                <li
                  key={section.id}
                  className={`list-item ${dragSectionId === section.id ? 'is-dragging' : ''}`}
                  draggable={canEdit}
                  onDragStart={() => setDragSectionId(section.id)}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={() => void onDropSection(section.id)}
                >
                  <Link to={`/books/${bookId}/chapters/${chapterId}/sections/${section.id}`}>
                    <strong>{section.title}</strong>
                  </Link>
                  {canEdit && (
                    <div className="reorder-controls">
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

        <NotesPanel
          bookId={bookId}
          attachType="CHAPTER"
          chapterId={chapterId}
          canEdit={!!canEdit}
          notes={notes}
          onChanged={load}
        />
      </main>
    </div>
  )
}
