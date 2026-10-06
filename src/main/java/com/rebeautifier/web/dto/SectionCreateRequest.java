package com.rebeautifier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectionCreateRequest(
        @NotBlank @Size(max = 200) String title
) {
}
