package com.rebeautifier.web.dto;

import java.util.List;

/** Versioned JSON backup document for a single book. */
public record BookBackupDocument(
        String schema,
        int schemaVersion,
        BookPayload book
) {
    public static final String SCHEMA = "rebeautifier.book-backup";
    public static final int VERSION = 1;

    public record BookPayload(
            String title,
            String description,
            List<ChapterPayload> chapters,
            List<NotePayload> notes
    ) {
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
