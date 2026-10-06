package com.rebeautifier.web.dto;

import jakarta.validation.constraints.Size;

public record AcceptRevisionRequest(
        @Size(max = 200_000) String editedBodyRich
) {
}
