import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, type BookRole, type Member } from '../api'
import { useDialog } from './DialogProvider'

type Props = {
  bookId: string
  open: boolean
  onClose: () => void
  onChanged?: () => void
}

export function ShareDialog({ bookId, open, onClose, onChanged }: Props) {
  const { confirm } = useDialog()
  const [members, setMembers] = useState<Member[]>([])
  const [usernameOrEmail, setUsernameOrEmail] = useState('')
  const [role, setRole] = useState<'EDITOR' | 'VIEWER'>('EDITOR')
  const [transferTo, setTransferTo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function load() {
    try {
      const data = await api<Member[]>(`/api/v1/books/${bookId}/members`)
      setMembers(data)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load members')
    }
  }

  useEffect(() => {
    if (open) void load()
  }, [open, bookId])

  if (!open) return null

  async function invite(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      await api(`/api/v1/books/${bookId}/members`, {
        method: 'POST',
        body: JSON.stringify({ usernameOrEmail, role }),
      })
      setUsernameOrEmail('')
      await load()
      onChanged?.()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Invite failed')
    } finally {
      setBusy(false)
    }
  }

  async function changeRole(userId: string, next: BookRole) {
    if (next === 'OWNER') return
    setBusy(true)
    try {
      await api(`/api/v1/books/${bookId}/members/${userId}`, {
        method: 'PATCH',
        body: JSON.stringify({ role: next }),
      })
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change role')
    } finally {
      setBusy(false)
    }
  }

  async function remove(userId: string) {
    const ok = await confirm({
      title: 'Remove collaborator',
      message: 'Remove this collaborator from the book?',
      confirmLabel: 'Remove',
      cancelLabel: 'Cancel',
      danger: true,
    })
    if (!ok) return
    setBusy(true)
    try {
      await api(`/api/v1/books/${bookId}/members/${userId}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove member')
    } finally {
      setBusy(false)
    }
  }

  async function transfer(event: FormEvent) {
    event.preventDefault()
    const ok = await confirm({
      title: 'Transfer ownership',
      message: `Transfer ownership to ${transferTo}? You will become an Editor.`,
      confirmLabel: 'Transfer ownership',
      cancelLabel: 'Cancel',
      danger: true,
    })
    if (!ok) return
    setBusy(true)
    try {
      await api(`/api/v1/books/${bookId}/transfer`, {
        method: 'POST',
        body: JSON.stringify({ usernameOrEmail: transferTo }),
      })
      setTransferTo('')
      await load()
      onChanged?.()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Transfer failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="modal-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="share-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2 id="share-title">Share this book</h2>
          <button className="btn btn-ghost" type="button" onClick={onClose}>
            Close
          </button>
        </div>
        <p className="muted">Invite someone on this install as Editor or Viewer. Notes inherit book access.</p>

        {error && <div className="error-banner">{error}</div>}

        <form className="share-form" onSubmit={(e) => void invite(e)}>
          <label>
            Username or email
            <input
              value={usernameOrEmail}
              onChange={(e) => setUsernameOrEmail(e.target.value)}
              required
              placeholder="editor"
            />
          </label>
          <label>
            Role
            <select value={role} onChange={(e) => setRole(e.target.value as 'EDITOR' | 'VIEWER')}>
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </label>
          <button className="btn btn-primary" type="submit" disabled={busy}>
            Add collaborator
          </button>
        </form>

        <ul className="list share-list">
          {members.map((m) => (
            <li key={m.userId} className="list-item">
              <div>
                <strong>{m.displayName}</strong>
                <div className="muted">@{m.username}</div>
              </div>
              <div className="share-actions">
                {m.role === 'OWNER' ? (
                  <span className="status-pill">OWNER</span>
                ) : (
                  <>
                    <select
                      value={m.role}
                      disabled={busy}
                      onChange={(e) => void changeRole(m.userId, e.target.value as BookRole)}
                    >
                      <option value="EDITOR">EDITOR</option>
                      <option value="VIEWER">VIEWER</option>
                    </select>
                    <button className="btn btn-danger" type="button" disabled={busy} onClick={() => void remove(m.userId)}>
                      Remove
                    </button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>

        <form className="share-form transfer-form" onSubmit={(e) => void transfer(e)}>
          <h3>Transfer ownership</h3>
          <p className="muted panel-sub">Hand the book to another account on this install. You stay as Editor.</p>
          <label>
            New owner (username or email)
            <input
              value={transferTo}
              onChange={(e) => setTransferTo(e.target.value)}
              required
              placeholder="editor"
            />
          </label>
          <button className="btn btn-ghost" type="submit" disabled={busy || !transferTo.trim()}>
            Transfer ownership
          </button>
        </form>
      </div>
    </div>
  )
}
