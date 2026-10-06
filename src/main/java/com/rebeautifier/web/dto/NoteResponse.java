package com.rebeautifier.web.dto;

import com.rebeautifier.domain.NoteAttachType;
import com.rebeautifier.domain.NoteStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoteResponse(
        UUID id,
        NoteAttachType attachType,
        UUID bookId,
        UUID chapterId,
        UUID sectionId,
        String title,
        String bodyRich,
        String bodyOriginal,
        String bodyPlain,
        NoteStatus status,
        Integer pageStart,
        Integer pageEnd,
        List<String> tags,
        int position,
        UUID createdById,
        String createdByDisplayName,
        long contentVersion,
        Instant createdAt,
        Instant updatedAt
) {
}
