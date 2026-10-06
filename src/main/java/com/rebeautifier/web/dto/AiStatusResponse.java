package com.rebeautifier.web.dto;

import java.util.List;

public record AiStatusResponse(
        boolean available,
        boolean enabled,
        String provider,
        boolean reachable,
        boolean modelAvailable,
        String model,
        String baseUrl,
        String message,
        long latencyMs,
        int inFlight,
        int maxConcurrent,
        List<String> models,
        String setupHint,
        long metricsAttempts,
        long metricsSucceeded,
        long metricsFailed,
        long metricsAverageLatencyMs
) {
}
