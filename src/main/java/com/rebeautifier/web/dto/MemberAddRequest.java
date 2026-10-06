package com.rebeautifier.web.dto;

import com.rebeautifier.domain.BookRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberAddRequest(
        @NotBlank String usernameOrEmail,
        @NotNull BookRole role
) {
}
