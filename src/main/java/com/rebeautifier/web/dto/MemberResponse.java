package com.rebeautifier.web.dto;

import com.rebeautifier.domain.BookRole;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String username,
        String displayName,
        BookRole role,
        Instant invitedAt,
        Instant acceptedAt
) {
}
