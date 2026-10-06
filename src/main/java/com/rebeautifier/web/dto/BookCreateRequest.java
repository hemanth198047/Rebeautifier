package com.rebeautifier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description
) {
}
