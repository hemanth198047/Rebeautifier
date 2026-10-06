package com.rebeautifier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebeautifier.domain.Book;
import com.rebeautifier.domain.Chapter;
import com.rebeautifier.domain.Note;
import com.rebeautifier.domain.NoteAttachType;
import com.rebeautifier.domain.NoteStatus;
import com.rebeautifier.domain.Section;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.ChapterRepository;
import com.rebeautifier.repository.NoteRepository;
import com.rebeautifier.repository.SectionRepository;
import com.rebeautifier.web.dto.NoteCreateRequest;
import com.rebeautifier.web.dto.NoteResponse;
import com.rebeautifier.web.dto.NoteUpdateRequest;
import com.rebeautifier.web.dto.ReorderRequest;
import com.rebeautifier.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NoteService {

    private static final Pattern TAG_STRIP = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final NoteRepository noteRepository;
    private final ChapterRepository chapterRepository;
    private final SectionRepository sectionRepository;
    private final AccessControlService accessControlService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public NoteService(NoteRepository noteRepository,
                       ChapterRepository chapterRepository,
                       SectionRepository sectionRepository,
                       AccessControlService accessControlService,
                       AuthService authService,
                       ObjectMapper objectMapper) {
        this.noteRepository = noteRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.accessControlService = accessControlService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID bookId, UUID chapterId, UUID sectionId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.readers());
        return noteRepository.findFiltered(bookId, chapterId, sectionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteResponse get(UUID noteId) {
        User user = authService.requireCurrentUser();
        Note note = requireNote(noteId);
        accessControlService.requireRole(note.getBook().getId(), user, AccessControlService.readers());
        return toResponse(note);
    }

    @Transactional
    public NoteResponse create(UUID bookId, NoteCreateRequest request) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());
        Book book = accessControlService.requireBook(bookId);

        Note note = new Note();
        note.setBook(book);
        note.setCreatedBy(user);
        note.setAttachType(request.attachType());
        note.setTitle(blankToNull(request.title()));
        note.setStatus(request.status() == null ? NoteStatus.DRAFT : request.status());
        applyPageRange(note, request.pageStart(), request.pageEnd(), false);
        note.setTags(serializeTags(request.tags()));

        attachParents(note, book, request.attachType(), request.chapterId(), request.sectionId());
        note.setPosition(nextPosition(note));

        String body = normalizeBody(request.bodyRich());
        note.setBodyRich(body);
        note.setBodyPlain(toPlain(body));
        if (note.getStatus() == NoteStatus.SAVED && body != null) {
            note.setBodyOriginal(body);
        }

        noteRepository.save(note);
        return toResponse(note);
    }

    @Transactional
    public NoteResponse update(UUID noteId, NoteUpdateRequest request) {
        User user = authService.requireCurrentUser();
        Note note = requireNote(noteId);
        accessControlService.requireRole(note.getBook().getId(), user, AccessControlService.editors());

        if (!Objects.equals(note.getContentVersion(), request.expectedContentVersion())) {
            throw new ApiException(
                    "VERSION_CONFLICT",
                    "Note was updated elsewhere. Reload before saving.",
                    org.springframework.http.HttpStatus.CONFLICT
            );
        }

        boolean bodyChanged = false;
        if (request.title() != null) {
            note.setTitle(blankToNull(request.title()));
        }
        if (request.bodyRich() != null) {
            String body = normalizeBody(request.bodyRich());
            if (!Objects.equals(body, note.getBodyRich())) {
                note.setBodyRich(body);
                note.setBodyPlain(toPlain(body));
                bodyChanged = true;
            }
        }
        if (request.status() != null) {
            note.setStatus(request.status());
        }
        if (Boolean.TRUE.equals(request.clearPageRange())) {
            note.setPageStart(null);
            note.setPageEnd(null);
        } else if (request.pageStart() != null || request.pageEnd() != null) {
            Integer start = request.pageStart() != null ? request.pageStart() : note.getPageStart();
            Integer end = request.pageEnd() != null ? request.pageEnd() : note.getPageEnd();
            applyPageRange(note, start, end, true);
        }
        if (request.tags() != null) {
            note.setTags(serializeTags(request.tags()));
        }

        if (bodyChanged) {
            note.bumpContentVersion();
        }

        // First save snapshot: when marking SAVED (or already SAVED after body set), lock original once.
        if (note.getBodyOriginal() == null && note.getBodyRich() != null
                && (note.getStatus() == NoteStatus.SAVED || request.status() == NoteStatus.SAVED)) {
            note.setBodyOriginal(note.getBodyRich());
        }

        return toResponse(note);
    }

    @Transactional
    public void delete(UUID noteId) {
        User user = authService.requireCurrentUser();
        Note note = requireNote(noteId);
        UUID bookId = note.getBook().getId();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());
        NoteAttachType attachType = note.getAttachType();
        UUID chapterId = note.getChapter() != null ? note.getChapter().getId() : null;
        UUID sectionId = note.getSection() != null ? note.getSection().getId() : null;
        noteRepository.delete(note);
        densifyPositions(bookId, attachType, chapterId, sectionId);
    }

    @Transactional
    public List<NoteResponse> reorder(ReorderRequest request, UUID bookId, NoteAttachType attachType,
                                      UUID chapterId, UUID sectionId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());

        List<Note> notes = switch (attachType) {
            case BOOK -> noteRepository.findByBookIdAndAttachTypeOrderByPositionAsc(bookId, NoteAttachType.BOOK);
            case CHAPTER -> {
                requireChapterInBook(chapterId, bookId);
                yield noteRepository.findByChapterIdAndAttachTypeOrderByPositionAsc(chapterId, NoteAttachType.CHAPTER);
            }
            case SECTION -> {
                Section section = requireSectionInBook(sectionId, bookId);
                if (chapterId != null && !section.getChapter().getId().equals(chapterId)) {
                    throw ApiException.badRequest("Section does not belong to the given chapter");
                }
                yield noteRepository.findBySectionIdAndAttachTypeOrderByPositionAsc(sectionId, NoteAttachType.SECTION);
            }
        };

        if (notes.size() != request.orderedIds().size()) {
            throw ApiException.badRequest("Reorder list must include every note exactly once");
        }

        Map<UUID, Note> byId = new HashMap<>();
        for (Note note : notes) {
            byId.put(note.getId(), note);
        }
        Set<UUID> seen = new HashSet<>();
        int offset = notes.size() + 1000;
        for (int i = 0; i < notes.size(); i++) {
            notes.get(i).setPosition(offset + i);
        }
        noteRepository.flush();
        for (int i = 0; i < request.orderedIds().size(); i++) {
            UUID id = request.orderedIds().get(i);
            Note note = byId.get(id);
            if (note == null || !seen.add(id)) {
                throw ApiException.badRequest("Invalid note id in reorder list");
            }
            note.setPosition(i);
        }

        return notes.stream()
                .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Note requireNote(UUID noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> ApiException.notFound("Note not found"));
    }

    private void densifyPositions(UUID bookId, NoteAttachType attachType, UUID chapterId, UUID sectionId) {
        List<Note> remaining = switch (attachType) {
            case BOOK -> noteRepository.findByBookIdAndAttachTypeOrderByPositionAsc(bookId, NoteAttachType.BOOK);
            case CHAPTER -> noteRepository.findByChapterIdAndAttachTypeOrderByPositionAsc(chapterId, NoteAttachType.CHAPTER);
            case SECTION -> noteRepository.findBySectionIdAndAttachTypeOrderByPositionAsc(sectionId, NoteAttachType.SECTION);
        };
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
    }

    private int nextPosition(Note note) {
        return switch (note.getAttachType()) {
            case BOOK -> (int) noteRepository.countByBookIdAndAttachType(note.getBook().getId(), NoteAttachType.BOOK);
            case CHAPTER -> (int) noteRepository.countByChapterIdAndAttachType(note.getChapter().getId(), NoteAttachType.CHAPTER);
            case SECTION -> (int) noteRepository.countBySectionIdAndAttachType(note.getSection().getId(), NoteAttachType.SECTION);
        };
    }

    private void attachParents(Note note, Book book, NoteAttachType attachType, UUID chapterId, UUID sectionId) {
        switch (attachType) {
            case BOOK -> {
                if (chapterId != null || sectionId != null) {
                    throw ApiException.badRequest("Book-level notes must not set chapterId or sectionId");
                }
                note.setChapter(null);
                note.setSection(null);
            }
            case CHAPTER -> {
                if (chapterId == null || sectionId != null) {
                    throw ApiException.badRequest("Chapter-level notes require chapterId and no sectionId");
                }
                Chapter chapter = requireChapterInBook(chapterId, book.getId());
                note.setChapter(chapter);
                note.setSection(null);
            }
            case SECTION -> {
                if (sectionId == null) {
                    throw ApiException.badRequest("Section-level notes require sectionId");
                }
                Section section = requireSectionInBook(sectionId, book.getId());
                if (chapterId != null && !section.getChapter().getId().equals(chapterId)) {
                    throw ApiException.badRequest("sectionId does not belong to chapterId");
                }
                note.setChapter(section.getChapter());
                note.setSection(section);
            }
        }
    }

    private Chapter requireChapterInBook(UUID chapterId, UUID bookId) {
        if (chapterId == null) {
            throw ApiException.badRequest("chapterId is required");
        }
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> ApiException.notFound("Chapter not found"));
        if (!chapter.getBook().getId().equals(bookId)) {
            throw ApiException.badRequest("Chapter does not belong to this book");
        }
        return chapter;
    }

    private Section requireSectionInBook(UUID sectionId, UUID bookId) {
        if (sectionId == null) {
            throw ApiException.badRequest("sectionId is required");
        }
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> ApiException.notFound("Section not found"));
        if (!section.getChapter().getBook().getId().equals(bookId)) {
            throw ApiException.badRequest("Section does not belong to this book");
        }
        return section;
    }

    private void applyPageRange(Note note, Integer pageStart, Integer pageEnd, boolean partial) {
        if (pageStart != null && pageStart < 1) {
            throw ApiException.badRequest("pageStart must be >= 1");
        }
        if (pageEnd != null && pageEnd < 1) {
            throw ApiException.badRequest("pageEnd must be >= 1");
        }
        if (pageStart != null && pageEnd != null && pageEnd < pageStart) {
            throw ApiException.badRequest("pageEnd must be >= pageStart");
        }
        if (!partial || pageStart != null) {
            note.setPageStart(pageStart);
        }
        if (!partial || pageEnd != null) {
            note.setPageEnd(pageEnd);
        }
        if (note.getPageStart() != null && note.getPageEnd() != null
                && note.getPageEnd() < note.getPageStart()) {
            throw ApiException.badRequest("pageEnd must be >= pageStart");
        }
    }

    private NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getAttachType(),
                note.getBook().getId(),
                note.getChapter() != null ? note.getChapter().getId() : null,
                note.getSection() != null ? note.getSection().getId() : null,
                note.getTitle(),
                note.getBodyRich(),
                note.getBodyOriginal(),
                note.getBodyPlain(),
                note.getStatus(),
                note.getPageStart(),
                note.getPageEnd(),
                deserializeTags(note.getTags()),
                note.getPosition(),
                note.getCreatedBy().getId(),
                note.getCreatedBy().getDisplayName(),
                note.getContentVersion(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    private String serializeTags(List<String> tags) {
        if (tags == null) {
            return "[]";
        }
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim().toLowerCase();
            if (trimmed.isEmpty() || trimmed.length() > 40) {
                continue;
            }
            if (seen.add(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Invalid tags");
        }
    }

    private List<String> deserializeTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw, STRING_LIST);
            return tags == null ? List.of() : List.copyOf(tags);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String normalizeBody(String bodyRich) {
        if (bodyRich == null) {
            return null;
        }
        String trimmed = bodyRich.trim();
        if (trimmed.isEmpty() || trimmed.equals("<p></p>") || trimmed.equals("<p><br></p>")) {
            return null;
        }
        return bodyRich;
    }

    private String toPlain(String bodyRich) {
        if (bodyRich == null) {
            return null;
        }
        String plain = TAG_STRIP.matcher(bodyRich).replaceAll(" ");
        plain = WHITESPACE.matcher(plain).replaceAll(" ").trim();
        return plain.isEmpty() ? null : plain;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
