package com.rebeautifier.ai;

import com.rebeautifier.domain.AiProvider;

public record AiRuntimeConfig(
        AiProvider provider,
        String baseUrl,
        String model,
        String apiKey
) {
}
