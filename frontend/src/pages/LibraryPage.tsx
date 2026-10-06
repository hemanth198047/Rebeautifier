import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, formatTimestamp, type Book } from '../api'
import { AppShell, PageHeader } from '../components/AppShell'

export function LibraryPage() {
  const [books, setBooks] = useState<Book[]>([])
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<'ALL' | 'OWNER' | 'EDITOR' | 'VIEWER'>('ALL')
  const [query, setQuery] = useState('')

  async function load() {
    setLoading(true)
    try {
      const data = await api<Book[]>('/api/v1/books')
      setBooks(data)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load library')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const stats = useMemo(() => {
    const owned = books.filter((b) => b.myRole === 'OWNER').length
    const shared = books.filter((b) => b.myRole !== 'OWNER').length
    const withCover = books.filter((b) => !!b.coverUrl).length
    return { total: books.length, owned, shared, withCover }
  }, [books])

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return books.filter((book) => {
      if (filter !== 'ALL' && book.myRole !== filter) return false
      if (!q) return true
      return (
        book.title.toLowerCase().includes(q) ||
        (book.description ?? '').toLowerCase().includes(q) ||
        book.ownerDisplayName.toLowerCase().includes(q)
      )
    })
  }, [books, filter, query])

  async function onCreate(event: FormEvent) {
    event.preventDefault()
    try {
      await api<Book>('/api/v1/books', {
        method: 'POST',
        body: JSON.stringify({ title, description }),
      })
      setTitle('')
      setDescription('')
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create book')
    }
  }

  return (
    <AppShell active="library" compactAi={false}>
      <main className="page page-dense">
        <PageHeader
          eyebrow="Workspace"
          title="Your library"
          subtitle="Books you own or share — open one to wander its chapters, capture notes, and refine when ready."
          actions={
            <>
              <Link className="btn btn-ghost" to="/search">
                Search notes
              </Link>
              <Link className="btn btn-ghost" to="/backup">
                Backup
              </Link>
              <a className="btn btn-primary" href="#start-book">
                Start a book
              </a>
            </>
          }
          meta={[
            { label: 'Books', value: stats.total },
            { label: 'Owned', value: stats.owned },
            { label: 'Shared with you', value: stats.shared },
            { label: 'With covers', value: stats.withCover },
          ]}
        />

        {error && <div className="error-banner">{error}</div>}

        <div className="library-layout">
          <aside className="library-sidebar">
            <section id="start-book" className="create-panel dense-panel">
              <div className="panel-heading">
                <h2>Start a book</h2>
                <p className="muted panel-sub">Title required. Description and cover can wait.</p>
              </div>
              <form className="form-stack" onSubmit={onCreate}>
                <label>
                  Title
                  <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
                </label>
                <label>
                  Description
                  <textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows={4}
                    maxLength={5000}
                    placeholder="What belongs on this shelf?"
                  />
                </label>
                <button className="btn btn-primary" type="submit">
                  Create book
                </button>
              </form>
            </section>

            <section className="dense-panel library-tips">
              <h2>Operator tips</h2>
              <ul>
                <li>Add a cover from free image sites once the book exists.</li>
                <li>Invite editors from the book page Share dialog.</li>
                <li>Export JSON from Backup before major reshuffles.</li>
                <li>AI refine stays optional — notes work offline.</li>
              </ul>
            </section>
          </aside>

          <section className="library-main">
            <div className="library-toolbar">
              <label className="library-filter-search">
                <span className="sr-only">Filter books</span>
                <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Filter by title, description, owner…"
                />
              </label>
              <div className="filter-row" role="tablist" aria-label="Role filter">
                {(['ALL', 'OWNER', 'EDITOR', 'VIEWER'] as const).map((role) => (
                  <button
                    key={role}
                    type="button"
                    className={`filter-chip${filter === role ? ' is-active' : ''}`}
                    onClick={() => setFilter(role)}
                  >
                    {role === 'ALL' ? 'All roles' : role.toLowerCase()}
                  </button>
                ))}
              </div>
            </div>

            {loading ? (
              <p className="muted">Opening shelves…</p>
            ) : visible.length === 0 ? (
              <div className="empty-state">
                {books.length === 0
                  ? 'No books yet. Create one in the sidebar to begin.'
                  : 'No books match this filter.'}
              </div>
            ) : (
              <div className="book-grid book-grid-dense">
                {visible.map((book, index) => (
                  <Link
                    key={book.id}
                    to={`/books/${book.id}`}
                    className="book-tile book-tile-dense"
                    style={{ animationDelay: `${index * 0.04}s` }}
                  >
                    {book.coverUrl ? (
                      <img className="book-cover" src={book.coverUrl} alt="" />
                    ) : (
                      <div className="book-cover book-cover-empty" aria-hidden>
                        <span>No cover</span>
                      </div>
                    )}
                    <div className="book-tile-body">
                      <div className="book-tile-top">
                        <h2>{book.title}</h2>
                        <span className={`role-pill role-${book.myRole.toLowerCase()}`}>
                          {book.myRole.toLowerCase()}
                        </span>
                      </div>
                      <p>{book.description || 'No description yet.'}</p>
                      <div className="book-tile-meta">
                        <span>{book.ownerDisplayName}</span>
                        <span>Updated {formatTimestamp(book.updatedAt)}</span>
                      </div>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </section>
        </div>
      </main>
    </AppShell>
  )
}
