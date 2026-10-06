package com.rebeautifier.web;

import com.rebeautifier.domain.FreeImageSource;
import com.rebeautifier.service.FreeCoverImageService;
import com.rebeautifier.web.dto.FreeCoverImageSearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cover-images")
public class CoverImageController {

    private final FreeCoverImageService freeCoverImageService;

    public CoverImageController(FreeCoverImageService freeCoverImageService) {
        this.freeCoverImageService = freeCoverImageService;
    }

    @GetMapping("/sources")
    public List<Map<String, String>> sources() {
        return Arrays.stream(FreeImageSource.values())
                .map(source -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("id", source.name());
                    row.put("label", switch (source) {
                        case OPENVERSE -> "Openverse (Creative Commons)";
                        case PICSUM -> "Lorem Picsum (Unsplash samples)";
                        case WIKIMEDIA -> "Wikimedia Commons";
                    });
                    row.put("blurb", switch (source) {
                        case OPENVERSE -> "Search millions of openly licensed photos — no API key.";
                        case PICSUM -> "Curated free sample photos. Browse pages; query filters by photographer.";
                        case WIKIMEDIA -> "Public-domain and freely licensed media from Wikimedia Commons.";
                    });
                    return row;
                })
                .toList();
    }

    @GetMapping("/search")
    public FreeCoverImageSearchResponse search(
            @RequestParam(defaultValue = "OPENVERSE") FreeImageSource source,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "18") int pageSize
    ) {
        return freeCoverImageService.search(source, q, page, pageSize);
    }
}
