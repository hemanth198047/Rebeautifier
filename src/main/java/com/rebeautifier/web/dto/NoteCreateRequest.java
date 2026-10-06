package com.rebeautifier.web.dto;

import com.rebeautifier.domain.NoteAttachType;
import com.rebeautifier.domain.NoteStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record NoteCreateRequest(
        @NotNull NoteAttachType attachType,
        UUID chapterId,
        UUID sectionId,
        @Size(max = 200) String title,
        String bodyRich,
        NoteStatus status,
        Integer pageStart,
        Integer pageEnd,
        List<@Size(max = 40) String> tags
) {
}
