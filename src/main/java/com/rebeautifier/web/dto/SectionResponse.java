package com.rebeautifier.web.dto;

import java.time.Instant;
import java.util.UUID;

public record SectionResponse(
        UUID id,
        UUID chapterId,
        String title,
        int position,
        Instant createdAt,
        Instant updatedAt
) {
}
