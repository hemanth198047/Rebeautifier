package com.rebeautifier.web.dto;

import com.rebeautifier.domain.FreeImageSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SetCoverFromFreeImageRequest(
        @NotNull FreeImageSource source,
        @NotBlank String imageId,
        String imageUrl
) {
}
