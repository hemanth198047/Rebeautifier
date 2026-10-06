import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, ApiError, type SearchResponse } from '../api'
import { useAuth } from '../auth'
import { AiStatusChip } from '../components/AiStatusChip'

export function SearchPage() {
  const { user, logout } = useAuth()
  const [params, setParams] = useSearchParams()
  const initial = params.get('q') ?? ''
  const [query, setQuery] = useState(initial)
  const [result, setResult] = useState<SearchResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const grouped = useMemo(() => {
    const hits = result?.hits ?? []
    return {
      BOOK: hits.filter((h) => h.type === 'BOOK'),
      CHAPTER: hits.filter((h) => h.type === 'CHAPTER'),
      SECTION: hits.filter((h) => h.type === 'SECTION'),
      NOTE: hits.filter((h) => h.type === 'NOTE'),
    }
  }, [result])

  async function runSearch(q: string) {
    const trimmed = q.trim()
    if (trimmed.length < 2) {
      setResult(null)
      setError(trimmed.length === 0 ? null : 'Enter at least 2 characters')
      return
    }
    setLoading(true)
    setError(null)
    try {
      const data = await api<SearchResponse>(`/api/v1/search?q=${encodeURIComponent(trimmed)}`)
      setResult(data)
      setParams(trimmed ? { q: trimmed } : {})
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Search failed')
      setResult(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (initial.trim().length >= 2) {
      void runSearch(initial)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void runSearch(query)
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/library" className="brand">
          Rebeautifier<span>.</span>
        </Link>
        <div className="topbar-actions">
          <Link className="btn btn-ghost" to="/library">
            Library
          </Link>
          <Link className="btn btn-ghost" to="/backup">
            Backup
          </Link>
          <Link className="btn btn-ghost" to="/settings">
            Settings
          </Link>
          <AiStatusChip compact />
          <span className="muted">{user?.displayName}</span>
          <button className="btn btn-ghost" type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="page">
        <div className="library-header">
          <div>
            <h1>Search</h1>
            <p className="muted">Find books, chapters, sections, and notes you can access.</p>
          </div>
        </div>

        <form className="search-form" onSubmit={onSubmit}>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search titles and note text…"
            autoFocus
          />
          <button className="btn btn-primary" type="submit" disabled={loading}>
            {loading ? 'Searching…' : 'Search'}
          </button>
        </form>

        {error && <div className="error-banner">{error}</div>}

        {result && result.hits.length === 0 && (
          <div className="empty-state">No matches for “{result.query}”.</div>
        )}

        {result && result.hits.length > 0 && (
          <div className="search-results">
            {(['BOOK', 'CHAPTER', 'SECTION', 'NOTE'] as const).map((type) => {
              const hits = grouped[type]
              if (hits.length === 0) return null
              return (
                <section key={type} className="detail-panel">
                  <h2>{type.charAt(0) + type.slice(1).toLowerCase()}s</h2>
                  <ul className="list">
                    {hits.map((hit) => (
                      <li key={`${hit.type}-${hit.id}`} className="list-item">
                        <Link to={hit.href} className="note-link">
                          <div className="note-link-top">
                            <strong>{hit.title}</strong>
                            <span className="status-pill">{hit.type}</span>
                          </div>
                          <div className="note-meta">
                            <span>{hit.bookTitle}</span>
                            {hit.chapterTitle && <span>{hit.chapterTitle}</span>}
                            {hit.sectionTitle && <span>{hit.sectionTitle}</span>}
                          </div>
                          {hit.snippet && <p className="note-excerpt">{hit.snippet}</p>}
                        </Link>
                      </li>
                    ))}
                  </ul>
                </section>
              )
            })}
          </div>
        )}
      </main>
    </div>
  )
}
