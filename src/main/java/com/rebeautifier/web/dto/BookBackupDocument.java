package com.rebeautifier.web.dto;

import java.util.List;

/** Versioned JSON backup document for a single book. */
public record BookBackupDocument(
        String schema,
        int schemaVersion,
        BookPayload book
) {
    public static final String SCHEMA = "rebeautifier.book-backup";
    /** Current export version. Import accepts 1 and 2. */
    public static final int VERSION = 2;
    public static final int MIN_SUPPORTED_VERSION = 1;

    public record BookPayload(
            String title,
            String description,
            /** Optional cover MIME (e.g. image/png) when coverBase64 is present. */
            String coverMime,
            /** Optional base64-encoded cover bytes (schema v2+). */
            String coverBase64,
            List<ChapterPayload> chapters,
            List<NotePayload> notes
    ) {
        /** Backward-compatible constructor for schema v1 payloads without covers. */
        public BookPayload(String title, String description, List<ChapterPayload> chapters, List<NotePayload> notes) {
            this(title, description, null, null, chapters, notes);
        }
    }

    public record ChapterPayload(
            String title,
            int position,
            List<SectionPayload> sections,
            List<NotePayload> notes
    ) {
    }

    public record SectionPayload(
            String title,
            int position,
            List<NotePayload> notes
    ) {
    }

    public record NotePayload(
            String attachType,
            String title,
            String bodyRich,
            String bodyOriginal,
            String bodyPlain,
            String status,
            Integer pageStart,
            Integer pageEnd,
            List<String> tags,
            int position
    ) {
    }
}
