package com.rebeautifier.service;

import com.rebeautifier.domain.Book;
import com.rebeautifier.domain.Chapter;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.ChapterRepository;
import com.rebeautifier.web.dto.ChapterCreateRequest;
import com.rebeautifier.web.dto.ChapterResponse;
import com.rebeautifier.web.dto.ChapterUpdateRequest;
import com.rebeautifier.web.dto.ReorderRequest;
import com.rebeautifier.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final AccessControlService accessControlService;
    private final AuthService authService;

    public ChapterService(ChapterRepository chapterRepository,
                          AccessControlService accessControlService,
                          AuthService authService) {
        this.chapterRepository = chapterRepository;
        this.accessControlService = accessControlService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<ChapterResponse> list(UUID bookId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.readers());
        return chapterRepository.findByBookIdOrderByPositionAsc(bookId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ChapterResponse create(UUID bookId, ChapterCreateRequest request) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());
        Book book = accessControlService.requireBook(bookId);
        Chapter chapter = new Chapter();
        chapter.setBook(book);
        chapter.setTitle(request.title().trim());
        chapter.setPosition(chapterRepository.countByBookId(bookId));
        chapterRepository.save(chapter);
        return toResponse(chapter);
    }

    @Transactional
    public ChapterResponse update(UUID chapterId, ChapterUpdateRequest request) {
        User user = authService.requireCurrentUser();
        Chapter chapter = requireChapter(chapterId);
        accessControlService.requireRole(chapter.getBook().getId(), user, AccessControlService.editors());
        if (request.title() != null && !request.title().isBlank()) {
            chapter.setTitle(request.title().trim());
        }
        return toResponse(chapter);
    }

    @Transactional
    public void delete(UUID chapterId) {
        User user = authService.requireCurrentUser();
        Chapter chapter = requireChapter(chapterId);
        UUID bookId = chapter.getBook().getId();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());
        int removedPosition = chapter.getPosition();
        chapterRepository.delete(chapter);
        List<Chapter> remaining = chapterRepository.findByBookIdOrderByPositionAsc(bookId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
        // silence unused warning if compiler is strict
        if (removedPosition < 0) {
            throw ApiException.badRequest("Invalid chapter position");
        }
    }

    @Transactional
    public List<ChapterResponse> reorder(UUID bookId, ReorderRequest request) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());
        List<Chapter> chapters = chapterRepository.findByBookIdOrderByPositionAsc(bookId);
        if (chapters.size() != request.orderedIds().size()) {
            throw ApiException.badRequest("Reorder list must include every chapter exactly once");
        }
        Map<UUID, Chapter> byId = new HashMap<>();
        for (Chapter chapter : chapters) {
            byId.put(chapter.getId(), chapter);
        }
        Set<UUID> seen = new HashSet<>();
        // First push positions out of the unique range to avoid collisions
        int offset = chapters.size() + 1000;
        for (int i = 0; i < chapters.size(); i++) {
            chapters.get(i).setPosition(offset + i);
        }
        chapterRepository.flush();
        for (int i = 0; i < request.orderedIds().size(); i++) {
            UUID id = request.orderedIds().get(i);
            Chapter chapter = byId.get(id);
            if (chapter == null || !seen.add(id)) {
                throw ApiException.badRequest("Invalid chapter id in reorder list");
            }
            chapter.setPosition(i);
        }
        return chapterRepository.findByBookIdOrderByPositionAsc(bookId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Chapter requireChapter(UUID chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> ApiException.notFound("Chapter not found"));
    }

    private ChapterResponse toResponse(Chapter chapter) {
        return new ChapterResponse(
                chapter.getId(),
                chapter.getBook().getId(),
                chapter.getTitle(),
                chapter.getPosition(),
                chapter.getCreatedAt(),
                chapter.getUpdatedAt()
        );
    }
}
