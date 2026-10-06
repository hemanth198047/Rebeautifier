package com.rebeautifier.web.dto;

import java.util.List;

public record FreeCoverImageSearchResponse(
        String query,
        String source,
        int page,
        int pageSize,
        Integer resultCount,
        List<FreeCoverImageHit> results
) {
}
