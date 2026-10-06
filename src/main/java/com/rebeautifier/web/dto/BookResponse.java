package com.rebeautifier.web.dto;

import com.rebeautifier.domain.BookRole;

import java.time.Instant;
import java.util.UUID;

public record BookResponse(
        UUID id,
        String title,
        String description,
        String coverUrl,
        UUID ownerId,
        String ownerDisplayName,
        BookRole myRole,
        Instant createdAt,
        Instant updatedAt
) {
}
