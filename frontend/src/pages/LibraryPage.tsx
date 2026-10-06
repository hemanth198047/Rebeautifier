import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, type Book } from '../api'
import { useAuth } from '../auth'
import { AiStatusChip } from '../components/AiStatusChip'

export function LibraryPage() {
  const { user, logout } = useAuth()
  const [books, setBooks] = useState<Book[]>([])
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

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
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          Rebeautifier<span>.</span>
        </div>
        <div className="topbar-actions">
          <Link className="btn btn-ghost" to="/search">
            Search
          </Link>
          <Link className="btn btn-ghost" to="/backup">
            Backup
          </Link>
          <AiStatusChip />
          <span className="muted">{user?.displayName}</span>
          <button className="btn btn-ghost" type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="page">
        <div className="library-header">
          <div>
            <h1>Your library</h1>
            <p className="muted">Books you own or share — open one to wander its chapters.</p>
          </div>
        </div>

        {error && <div className="error-banner">{error}</div>}

        <section className="create-panel">
          <h2>Start a book</h2>
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
                rows={3}
                maxLength={5000}
              />
            </label>
            <button className="btn btn-primary" type="submit">
              Create book
            </button>
          </form>
        </section>

        {loading ? (
          <p className="muted">Opening shelves…</p>
        ) : books.length === 0 ? (
          <div className="empty-state">No books yet. Create one above to begin.</div>
        ) : (
          <div className="book-grid">
            {books.map((book, index) => (
              <Link
                key={book.id}
                to={`/books/${book.id}`}
                className="book-tile"
                style={{ animationDelay: `${index * 0.05}s` }}
              >
                {book.coverUrl ? (
                  <img className="book-cover" src={book.coverUrl} alt="" />
                ) : (
                  <div className="book-cover" aria-hidden />
                )}
                <h2>{book.title}</h2>
                <p>{book.description || 'No description yet.'}</p>
                <div className="role-tag">{book.myRole.toLowerCase()}</div>
              </Link>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
