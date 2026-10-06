package com.rebeautifier.web.dto;

public record RateLimitStatusResponse(
        String storage,
        int windowSeconds,
        int authPerMinute,
        int authUsed,
        int authRemaining,
        int aiPerMinute,
        int aiUsed,
        int aiRemaining,
        String note
) {
}
