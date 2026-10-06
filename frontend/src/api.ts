export type User = {
  id: string
  username: string
  email: string
  displayName: string
}

export type BookRole = 'OWNER' | 'EDITOR' | 'VIEWER'

export type Book = {
  id: string
  title: string
  description: string | null
  coverUrl: string | null
  ownerId: string
  ownerDisplayName: string
  myRole: BookRole
  createdAt: string
  updatedAt: string
}

export type Chapter = {
  id: string
  bookId: string
  title: string
  position: number
  createdAt: string
  updatedAt: string
}

export type Section = {
  id: string
  chapterId: string
  title: string
  position: number
  createdAt: string
  updatedAt: string
}

export type NoteAttachType = 'BOOK' | 'CHAPTER' | 'SECTION'
export type NoteStatus = 'DRAFT' | 'SAVED'

export type Note = {
  id: string
  attachType: NoteAttachType
  bookId: string
  chapterId: string | null
  sectionId: string | null
  title: string | null
  bodyRich: string | null
  bodyOriginal: string | null
  bodyPlain: string | null
  status: NoteStatus
  pageStart: number | null
  pageEnd: number | null
  tags: string[]
  position: number
  createdById: string
  createdByDisplayName: string
  contentVersion: number
  createdAt: string
  updatedAt: string
}

export type Member = {
  userId: string
  username: string
  displayName: string
  role: BookRole
  invitedAt: string
  acceptedAt: string | null
}

export type RefineMode = 'GRAMMAR' | 'ORGANIZE' | 'SUMMARY' | 'TAKEAWAYS' | 'QA'
export type RevisionStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'STALE' | 'ACCEPTED' | 'REJECTED'

export type NoteRevision = {
  id: string
  noteId: string
  mode: RefineMode
  inputContentVersion: number
  inputHash: string
  status: RevisionStatus
  staleRelativeToNote: boolean
  resultRich: string | null
  resultPlain: string | null
  modelName: string | null
  errorMessage: string | null
  question: string | null
  createdById: string
  createdByDisplayName: string
  createdAt: string
  finalizedAt: string | null
}

export type AiStatus = {
  available: boolean
  reachable: boolean
  modelAvailable: boolean
  model: string
  baseUrl: string
  message: string
  latencyMs: number
  inFlight: number
  maxConcurrent: number
  models: string[]
  setupHint: string
  metricsAttempts: number
  metricsSucceeded: number
  metricsFailed: number
  metricsAverageLatencyMs: number
}

export type RateLimitStatus = {
  storage: string
  windowSeconds: number
  authPerMinute: number
  authUsed: number
  authRemaining: number
  aiPerMinute: number
  aiUsed: number
  aiRemaining: number
  note: string
}

export type SearchHit = {
  type: 'BOOK' | 'CHAPTER' | 'SECTION' | 'NOTE'
  id: string
  bookId: string
  bookTitle: string
  chapterId: string | null
  chapterTitle: string | null
  sectionId: string | null
  sectionTitle: string | null
  title: string
  snippet: string | null
  href: string
}

export type SearchResponse = {
  query: string
  hits: SearchHit[]
}

export class ApiError extends Error {
  code: string
  status: number
  retryAfterSeconds: number | null

  constructor(status: number, code: string, message: string, retryAfterSeconds: number | null = null) {
    super(message)
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

async function ensureCsrf(): Promise<void> {
  if (!readCookie('XSRF-TOKEN')) {
    await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    await ensureCsrf()
  }

  const headers = new Headers(init.headers)
  const csrf = readCookie('XSRF-TOKEN')
  if (csrf) {
    headers.set('X-XSRF-TOKEN', csrf)
  }
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'include',
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    const retryHeader = response.headers.get('Retry-After')
    const retryFromBody =
      typeof data?.retryAfterSeconds === 'number' ? data.retryAfterSeconds : null
    const retryAfterSeconds =
      retryFromBody ?? (retryHeader && /^\d+$/.test(retryHeader) ? Number(retryHeader) : null)
    const baseMessage = data?.message ?? 'Request failed'
    const message =
      data?.code === 'RATE_LIMITED' && retryAfterSeconds != null
        ? formatRateLimitedMessage(baseMessage, retryAfterSeconds)
        : baseMessage
    throw new ApiError(response.status, data?.code ?? 'ERROR', message, retryAfterSeconds)
  }

  return data as T
}

export function noteHref(note: Note): string {
  return `/books/${note.bookId}/notes/${note.id}`
}

export function formatRateLimitedMessage(baseMessage: string, retryAfterSeconds: number): string {
  const seconds = Math.max(1, Math.floor(retryAfterSeconds))
  const wait =
    seconds === 1 ? 'Try again in 1 second.' : `Try again in ${seconds} seconds.`
  const trimmed = baseMessage.trim()
  if (!trimmed) return wait
  return trimmed.endsWith('.') ? `${trimmed} ${wait}` : `${trimmed}. ${wait}`
}

export function formatPageRange(pageStart: number | null, pageEnd: number | null): string | null {
  if (pageStart == null && pageEnd == null) return null
  if (pageStart != null && pageEnd != null && pageStart !== pageEnd) {
    return `pp. ${pageStart}–${pageEnd}`
  }
  const page = pageStart ?? pageEnd
  return page != null ? `p. ${page}` : null
}

export function formatTimestamp(iso: string): string {
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}
