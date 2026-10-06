package com.rebeautifier.web.dto;

import com.rebeautifier.domain.NoteStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteUpdateRequest(
        @NotNull Long expectedContentVersion,
        @Size(max = 200) String title,
        String bodyRich,
        NoteStatus status,
        Integer pageStart,
        Integer pageEnd,
        Boolean clearPageRange,
        List<@Size(max = 40) String> tags
) {
}
