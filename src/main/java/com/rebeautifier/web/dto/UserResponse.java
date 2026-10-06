package com.rebeautifier.web.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String displayName
) {
}
