package com.rebeautifier.web.dto;

import jakarta.validation.constraints.Size;

public record BookUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String description
) {
}
