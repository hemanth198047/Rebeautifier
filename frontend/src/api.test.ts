import { describe, expect, it } from 'vitest'
import { formatPageRange, formatRateLimitedMessage, noteHref } from './api'

describe('formatRateLimitedMessage', () => {
  it('appends a singular retry hint', () => {
    expect(formatRateLimitedMessage('Too many AI refine requests.', 1)).toBe(
      'Too many AI refine requests. Try again in 1 second.',
    )
  })

  it('appends a plural retry hint and period when missing', () => {
    expect(formatRateLimitedMessage('Slow down', 12)).toBe(
      'Slow down. Try again in 12 seconds.',
    )
  })
})

describe('formatPageRange', () => {
  it('formats single and ranged pages', () => {
    expect(formatPageRange(null, null)).toBeNull()
    expect(formatPageRange(3, null)).toBe('p. 3')
    expect(formatPageRange(2, 5)).toBe('pp. 2–5')
  })
})

describe('noteHref', () => {
  it('builds a note path', () => {
    expect(
      noteHref({
        id: 'n1',
        bookId: 'b1',
        attachType: 'BOOK',
        chapterId: null,
        sectionId: null,
        title: null,
        bodyRich: null,
        bodyOriginal: null,
        bodyPlain: null,
        status: 'SAVED',
        pageStart: null,
        pageEnd: null,
        tags: [],
        position: 0,
        createdById: 'u1',
        createdByDisplayName: 'A',
        contentVersion: 1,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      }),
    ).toBe('/books/b1/notes/n1')
  })
})
