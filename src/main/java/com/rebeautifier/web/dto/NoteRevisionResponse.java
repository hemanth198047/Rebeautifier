package com.rebeautifier.web.dto;

import com.rebeautifier.domain.RefineMode;
import com.rebeautifier.domain.RevisionStatus;

import java.time.Instant;
import java.util.UUID;

public record NoteRevisionResponse(
        UUID id,
        UUID noteId,
        RefineMode mode,
        long inputContentVersion,
        String inputHash,
        RevisionStatus status,
        boolean staleRelativeToNote,
        String resultRich,
        String resultPlain,
        String modelName,
        String errorMessage,
        String question,
        UUID createdById,
        String createdByDisplayName,
        Instant createdAt,
        Instant finalizedAt
) {
}
