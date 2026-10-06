import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, type Book } from '../api'
import { useAuth } from '../auth'
import { AiStatusChip } from '../components/AiStatusChip'

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

async function ensureCsrf(): Promise<void> {
  if (!readCookie('XSRF-TOKEN')) {
    await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  }
}

export function BackupPage() {
  const { user, logout } = useAuth()
  const [books, setBooks] = useState<Book[]>([])
  const [bookId, setBookId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [importText, setImportText] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    void (async () => {
      try {
        const data = await api<Book[]>('/api/v1/books')
        setBooks(data)
        if (data[0]) setBookId(data[0].id)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Failed to load books')
      }
    })()
  }, [])

  async function download(format: 'json' | 'markdown') {
    if (!bookId) return
    setBusy(true)
    setError(null)
    try {
      await ensureCsrf()
      const response = await fetch(`/api/v1/books/${bookId}/export?format=${format}`, {
        credentials: 'include',
        headers: {
          'X-XSRF-TOKEN': readCookie('XSRF-TOKEN') ?? '',
        },
      })
      if (!response.ok) {
        const text = await response.text()
        const data = text ? JSON.parse(text) : null
        throw new ApiError(response.status, data?.code ?? 'ERROR', data?.message ?? 'Export failed')
      }
      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = format === 'json' ? `book-${bookId}.json` : `book-${bookId}.md`
      a.click()
      URL.revokeObjectURL(url)
      setSuccess(`Downloaded ${format} export`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Export failed')
    } finally {
      setBusy(false)
    }
  }

  async function onImport(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const created = await api<Book>('/api/v1/books/import', {
        method: 'POST',
        body: importText,
        headers: { 'Content-Type': 'application/json' },
      })
      setSuccess(`Restored “${created.title}” into your library`)
      setImportText('')
      const data = await api<Book[]>('/api/v1/books')
      setBooks(data)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Import failed')
    } finally {
      setBusy(false)
    }
  }

  async function onFile(file: File | null) {
    if (!file) return
    const text = await file.text()
    setImportText(text)
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
          <Link className="btn btn-ghost" to="/search">
            Search
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
            <h1>Backup & restore</h1>
            <p className="muted">Export a book as Markdown or JSON. Restore a validated JSON backup as a new book you own.</p>
          </div>
        </div>

        {error && <div className="error-banner">{error}</div>}
        {success && <div className="success-banner">{success}</div>}

        <section className="detail-panel">
          <h2>Export</h2>
          {books.length === 0 ? (
            <div className="empty-state">No books to export yet.</div>
          ) : (
            <>
              <label>
                Book
                <select value={bookId} onChange={(e) => setBookId(e.target.value)}>
                  {books.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.title}
                    </option>
                  ))}
                </select>
              </label>
              <div className="inline-form" style={{ marginTop: '1rem' }}>
                <button className="btn btn-primary" type="button" disabled={busy} onClick={() => void download('json')}>
                  Download JSON
                </button>
                <button className="btn btn-ghost" type="button" disabled={busy} onClick={() => void download('markdown')}>
                  Download Markdown
                </button>
              </div>
            </>
          )}
        </section>

        <section className="detail-panel">
          <h2>Restore JSON backup</h2>
          <p className="muted panel-sub">
            Paste a Rebeautifier backup (`schema: rebeautifier.book-backup`, version 1). Invalid files are rejected with a clear error.
          </p>
          <form className="form-stack" onSubmit={(e) => void onImport(e)}>
            <label className="btn btn-ghost" style={{ width: 'fit-content' }}>
              Choose JSON file
              <input
                type="file"
                accept="application/json,.json"
                hidden
                onChange={(e) => void onFile(e.target.files?.[0] ?? null)}
              />
            </label>
            <textarea
              value={importText}
              onChange={(e) => setImportText(e.target.value)}
              rows={14}
              placeholder='{"schema":"rebeautifier.book-backup","schemaVersion":1,"book":{...}}'
              required
            />
            <button className="btn btn-primary" type="submit" disabled={busy || !importText.trim()}>
              {busy ? 'Restoring…' : 'Restore as new book'}
            </button>
          </form>
        </section>
      </main>
    </div>
  )
}
