-- Phase 7: PostgreSQL full-text search indexes (expression GIN).

CREATE INDEX IF NOT EXISTS idx_books_fts ON books
    USING GIN (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, '')));

CREATE INDEX IF NOT EXISTS idx_chapters_fts ON chapters
    USING GIN (to_tsvector('english', coalesce(title, '')));

CREATE INDEX IF NOT EXISTS idx_sections_fts ON sections
    USING GIN (to_tsvector('english', coalesce(title, '')));

CREATE INDEX IF NOT EXISTS idx_notes_fts ON notes
    USING GIN (to_tsvector('english',
        coalesce(title, '') || ' ' || coalesce(body_plain, '') || ' ' || coalesce(tags, '')));
