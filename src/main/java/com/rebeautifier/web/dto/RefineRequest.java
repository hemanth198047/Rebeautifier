package com.rebeautifier.web.dto;

import com.rebeautifier.domain.RefineMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefineRequest(
        @NotNull RefineMode mode,
        @Size(max = 2000) String question
) {
}
