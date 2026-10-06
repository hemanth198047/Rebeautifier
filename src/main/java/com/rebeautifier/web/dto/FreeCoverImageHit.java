package com.rebeautifier.web.dto;

import com.rebeautifier.domain.FreeImageSource;

public record FreeCoverImageHit(
        FreeImageSource source,
        String imageId,
        String title,
        String thumbnailUrl,
        String fullUrl,
        String creator,
        String license,
        String attribution,
        String landingUrl,
        Integer width,
        Integer height
) {
}
