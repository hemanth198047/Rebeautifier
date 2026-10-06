package com.rebeautifier.web.dto;

public record AiPullResponse(
        boolean success,
        String message,
        boolean modelAvailable
) {
}
