package com.rebeautifier.web.dto;

import jakarta.validation.constraints.Size;

public record ChapterUpdateRequest(
        @Size(max = 200) String title
) {
}
