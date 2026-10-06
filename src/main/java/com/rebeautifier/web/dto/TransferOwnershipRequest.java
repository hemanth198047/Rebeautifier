package com.rebeautifier.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferOwnershipRequest(
        @NotBlank String usernameOrEmail
) {
}
