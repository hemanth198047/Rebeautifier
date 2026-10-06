-- Phase 3: AI refinement revisions (Ollama).

CREATE TABLE note_revisions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id                UUID         NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    mode                   VARCHAR(20)  NOT NULL,
    prompt_meta            TEXT,
    input_content_version  BIGINT       NOT NULL,
    input_hash             VARCHAR(64)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    result_rich            TEXT,
    result_plain           TEXT,
    model_name             VARCHAR(120),
    error_message          TEXT,
    created_by             UUID         NOT NULL REFERENCES users(id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finalized_at           TIMESTAMPTZ,
    CONSTRAINT ck_note_revisions_mode CHECK (
        mode IN ('GRAMMAR', 'ORGANIZE', 'SUMMARY', 'TAKEAWAYS', 'QA')
    ),
    CONSTRAINT ck_note_revisions_status CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'STALE', 'ACCEPTED', 'REJECTED')
    )
);

CREATE INDEX idx_note_revisions_note_id ON note_revisions(note_id);
CREATE INDEX idx_note_revisions_status ON note_revisions(status);
CREATE INDEX idx_note_revisions_created_at ON note_revisions(created_at DESC);
