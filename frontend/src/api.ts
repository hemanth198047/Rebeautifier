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

export class ApiError extends Error {
  code: string
  status: number

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
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
    throw new ApiError(
      response.status,
      data?.code ?? 'ERROR',
      data?.message ?? 'Request failed',
    )
  }

  return data as T
}
