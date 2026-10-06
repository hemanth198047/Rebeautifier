package com.rebeautifier.web.dto;

import java.util.List;

public record AiStatusResponse(
        boolean available,
        boolean reachable,
        boolean modelAvailable,
        String model,
        String baseUrl,
        String message,
        long latencyMs,
        int inFlight,
        int maxConcurrent,
        List<String> models,
        String setupHint
) {
}
