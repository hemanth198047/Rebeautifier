package com.rebeautifier.web.dto;

import jakarta.validation.constraints.Size;

public record SectionUpdateRequest(
        @Size(max = 200) String title
) {
}
