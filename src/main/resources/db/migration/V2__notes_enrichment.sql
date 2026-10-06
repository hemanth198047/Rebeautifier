-- Phase 2: enrich notes for rich-text editor workflow (draft/saved, pages, tags).

ALTER TABLE notes
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN page_start INT,
    ADD COLUMN page_end INT,
    ADD COLUMN tags TEXT;

ALTER TABLE notes
    ADD CONSTRAINT ck_notes_status CHECK (status IN ('DRAFT', 'SAVED'));

ALTER TABLE notes
    ADD CONSTRAINT ck_notes_page_range CHECK (
        page_start IS NULL
        OR page_end IS NULL
        OR page_end >= page_start
    );

CREATE INDEX idx_notes_status ON notes(status);
CREATE INDEX idx_notes_book_position ON notes(book_id, position);
