package com.rebeautifier.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ChapterResponse(
        UUID id,
        UUID bookId,
        String title,
        int position,
        Instant createdAt,
        Instant updatedAt
) {
}
