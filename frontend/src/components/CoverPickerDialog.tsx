import { useEffect, useState, type FormEvent } from 'react'
import {
  api,
  ApiError,
  type FreeCoverImageHit,
  type FreeCoverImageSearchResponse,
  type FreeCoverImageSource,
  type FreeCoverImageSourceInfo,
} from '../api'

type Props = {
  bookId: string
  bookTitle?: string
  open: boolean
  onClose: () => void
  onApplied: () => void
}

const SOURCE_FALLBACK: FreeCoverImageSourceInfo[] = [
  {
    id: 'OPENVERSE',
    label: 'Openverse (Creative Commons)',
    blurb: 'Search millions of openly licensed photos — no API key.',
  },
  {
    id: 'PICSUM',
    label: 'Lorem Picsum (Unsplash samples)',
    blurb: 'Curated free sample photos. Browse pages; query filters by photographer.',
  },
  {
    id: 'WIKIMEDIA',
    label: 'Wikimedia Commons',
    blurb: 'Public-domain and freely licensed media from Wikimedia Commons.',
  },
]

export function CoverPickerDialog({ bookId, bookTitle, open, onClose, onApplied }: Props) {
  const [sources, setSources] = useState<FreeCoverImageSourceInfo[]>(SOURCE_FALLBACK)
  const [source, setSource] = useState<FreeCoverImageSource>('OPENVERSE')
  const [query, setQuery] = useState(bookTitle?.trim() || 'library books')
  const [page, setPage] = useState(1)
  const [results, setResults] = useState<FreeCoverImageHit[]>([])
  const [resultCount, setResultCount] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)
  const [applyingId, setApplyingId] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    void api<FreeCoverImageSourceInfo[]>('/api/v1/cover-images/sources')
      .then(setSources)
      .catch(() => setSources(SOURCE_FALLBACK))
  }, [open])

  useEffect(() => {
    if (!open) return
    setQuery(bookTitle?.trim() || 'library books')
    setPage(1)
  }, [open, bookTitle])

  useEffect(() => {
    if (!open) return
    void runSearch(query, source, page)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, source, page])

  async function runSearch(q: string, src: FreeCoverImageSource, pageNum: number) {
    setSearching(true)
    setError(null)
    try {
      const params = new URLSearchParams({
        source: src,
        q: q.trim(),
        page: String(pageNum),
        pageSize: '18',
      })
      const data = await api<FreeCoverImageSearchResponse>(`/api/v1/cover-images/search?${params}`)
      setResults(data.results)
      setResultCount(data.resultCount)
    } catch (err) {
      setResults([])
      setResultCount(null)
      setError(err instanceof ApiError ? err.message : 'Could not search free images')
    } finally {
      setSearching(false)
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setPage(1)
    await runSearch(query, source, 1)
  }

  async function applyCover(hit: FreeCoverImageHit) {
    setApplyingId(hit.imageId)
    setError(null)
    try {
      await api(`/api/v1/books/${bookId}/cover/from-free-image`, {
        method: 'PUT',
        body: JSON.stringify({
          source: hit.source,
          imageId: hit.imageId,
          imageUrl: hit.fullUrl,
        }),
      })
      onApplied()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not set cover from free image')
    } finally {
      setApplyingId(null)
    }
  }

  if (!open) return null

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="modal-sheet cover-picker-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cover-picker-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2 id="cover-picker-title">Free cover images</h2>
          <button className="btn btn-ghost" type="button" onClick={onClose}>
            Close
          </button>
        </div>

        <p className="muted cover-picker-intro">
          Browse free stock and Creative Commons photos, then save one as this book&apos;s thumbnail. Images are
          downloaded into Rebeautifier storage (not hotlinked).
        </p>

        <div className="cover-source-tabs" role="tablist" aria-label="Image sources">
          {sources.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={source === item.id}
              className={`cover-source-tab${source === item.id ? ' is-active' : ''}`}
              onClick={() => {
                setSource(item.id)
                setPage(1)
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        <p className="muted cover-source-blurb">{sources.find((s) => s.id === source)?.blurb}</p>

        <form className="cover-search-form" onSubmit={(e) => void onSubmit(e)}>
          <label>
            Search
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={source === 'PICSUM' ? 'Optional photographer filter' : 'e.g. library, mountains, notebook'}
              maxLength={120}
            />
          </label>
          <button className="btn btn-primary" type="submit" disabled={searching}>
            {searching ? 'Searching…' : 'Search'}
          </button>
        </form>

        {error && <div className="error-banner">{error}</div>}

        {searching && results.length === 0 ? (
          <p className="muted">Searching free image sites…</p>
        ) : results.length === 0 ? (
          <div className="empty-state">No images found. Try another query or source.</div>
        ) : (
          <div className="cover-result-grid">
            {results.map((hit) => (
              <figure key={`${hit.source}-${hit.imageId}`} className="cover-result-card">
                <button
                  type="button"
                  className="cover-result-thumb"
                  disabled={applyingId !== null}
                  onClick={() => void applyCover(hit)}
                  title="Use as book cover"
                >
                  <img src={hit.thumbnailUrl} alt={hit.title || 'Free cover candidate'} loading="lazy" />
                  {applyingId === hit.imageId && <span className="cover-result-busy">Saving…</span>}
                </button>
                <figcaption>
                  <strong>{hit.title || 'Untitled'}</strong>
                  <span className="muted">
                    {[hit.creator, hit.license].filter(Boolean).join(' · ') || hit.attribution || hit.source}
                  </span>
                  {hit.landingUrl && (
                    <a href={hit.landingUrl} target="_blank" rel="noreferrer">
                      Source
                    </a>
                  )}
                </figcaption>
              </figure>
            ))}
          </div>
        )}

        <div className="cover-picker-pager">
          <button
            className="btn btn-ghost"
            type="button"
            disabled={page <= 1 || searching}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            Previous
          </button>
          <span className="muted">
            Page {page}
            {resultCount != null ? ` · ~${resultCount} matches` : ''}
          </span>
          <button
            className="btn btn-ghost"
            type="button"
            disabled={searching || results.length === 0}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      </div>
    </div>
  )
}
