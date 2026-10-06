package com.rebeautifier.web.dto;

import com.rebeautifier.domain.AiProvider;

import java.time.Instant;

public record AiSettingsResponse(
        boolean enabled,
        AiProvider provider,
        String model,
        String baseUrl,
        boolean apiKeyConfigured,
        String apiKeyHint,
        String pullMessage,
        Instant updatedAt
) {
}
