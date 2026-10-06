CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(64)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE books (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID         NOT NULL REFERENCES users(id),
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    cover_storage_key   VARCHAR(512),
    cover_mime          VARCHAR(100),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_books_owner_id ON books(owner_id);

CREATE TABLE book_members (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id      UUID        NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role         VARCHAR(20) NOT NULL,
    invited_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at  TIMESTAMPTZ,
    CONSTRAINT uq_book_members_book_user UNIQUE (book_id, user_id),
    CONSTRAINT ck_book_members_role CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
);

CREATE INDEX idx_book_members_user_id ON book_members(user_id);

CREATE TABLE chapters (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id     UUID         NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    position    INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chapters_book_position UNIQUE (book_id, position)
);

CREATE INDEX idx_chapters_book_id ON chapters(book_id);

CREATE TABLE sections (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id  UUID         NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    position    INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sections_chapter_position UNIQUE (chapter_id, position)
);

CREATE INDEX idx_sections_chapter_id ON sections(chapter_id);

-- Minimal notes table for hierarchy/FK integrity; full note editor is Phase 2.
CREATE TABLE notes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attach_type      VARCHAR(20)  NOT NULL,
    book_id          UUID         NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    chapter_id       UUID         REFERENCES chapters(id) ON DELETE CASCADE,
    section_id       UUID         REFERENCES sections(id) ON DELETE CASCADE,
    title            VARCHAR(200),
    body_rich        TEXT,
    body_original    TEXT,
    body_plain       TEXT,
    position         INT          NOT NULL DEFAULT 0,
    created_by       UUID         NOT NULL REFERENCES users(id),
    content_version  BIGINT       NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notes_attach_type CHECK (attach_type IN ('BOOK', 'CHAPTER', 'SECTION'))
);

CREATE INDEX idx_notes_book_id ON notes(book_id);
CREATE INDEX idx_notes_chapter_id ON notes(chapter_id);
CREATE INDEX idx_notes_section_id ON notes(section_id);
