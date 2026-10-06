package com.rebeautifier.web.dto;

import java.util.List;
import java.util.UUID;

public record SearchResponse(
        String query,
        List<SearchHit> hits
) {
    public record SearchHit(
            String type,
            UUID id,
            UUID bookId,
            String bookTitle,
            UUID chapterId,
            String chapterTitle,
            UUID sectionId,
            String sectionTitle,
            String title,
            String snippet,
            String href
    ) {
    }
}
