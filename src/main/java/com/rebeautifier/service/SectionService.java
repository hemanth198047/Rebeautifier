package com.rebeautifier.service;

import com.rebeautifier.domain.Chapter;
import com.rebeautifier.domain.Section;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.SectionRepository;
import com.rebeautifier.web.dto.ReorderRequest;
import com.rebeautifier.web.dto.SectionCreateRequest;
import com.rebeautifier.web.dto.SectionResponse;
import com.rebeautifier.web.dto.SectionUpdateRequest;
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
public class SectionService {

    private final SectionRepository sectionRepository;
    private final ChapterService chapterService;
    private final AccessControlService accessControlService;
    private final AuthService authService;

    public SectionService(SectionRepository sectionRepository,
                          ChapterService chapterService,
                          AccessControlService accessControlService,
                          AuthService authService) {
        this.sectionRepository = sectionRepository;
        this.chapterService = chapterService;
        this.accessControlService = accessControlService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<SectionResponse> list(UUID chapterId) {
        User user = authService.requireCurrentUser();
        Chapter chapter = chapterService.requireChapter(chapterId);
        accessControlService.requireRole(chapter.getBook().getId(), user, AccessControlService.readers());
        return sectionRepository.findByChapterIdOrderByPositionAsc(chapterId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SectionResponse create(UUID chapterId, SectionCreateRequest request) {
        User user = authService.requireCurrentUser();
        Chapter chapter = chapterService.requireChapter(chapterId);
        accessControlService.requireRole(chapter.getBook().getId(), user, AccessControlService.editors());
        Section section = new Section();
        section.setChapter(chapter);
        section.setTitle(request.title().trim());
        section.setPosition(sectionRepository.countByChapterId(chapterId));
        sectionRepository.save(section);
        return toResponse(section);
    }

    @Transactional
    public SectionResponse update(UUID sectionId, SectionUpdateRequest request) {
        User user = authService.requireCurrentUser();
        Section section = requireSection(sectionId);
        accessControlService.requireRole(section.getChapter().getBook().getId(), user, AccessControlService.editors());
        if (request.title() != null && !request.title().isBlank()) {
            section.setTitle(request.title().trim());
        }
        return toResponse(section);
    }

    @Transactional
    public void delete(UUID sectionId) {
        User user = authService.requireCurrentUser();
        Section section = requireSection(sectionId);
        UUID chapterId = section.getChapter().getId();
        accessControlService.requireRole(section.getChapter().getBook().getId(), user, AccessControlService.editors());
        sectionRepository.delete(section);
        List<Section> remaining = sectionRepository.findByChapterIdOrderByPositionAsc(chapterId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
    }

    @Transactional
    public List<SectionResponse> reorder(UUID chapterId, ReorderRequest request) {
        User user = authService.requireCurrentUser();
        Chapter chapter = chapterService.requireChapter(chapterId);
        accessControlService.requireRole(chapter.getBook().getId(), user, AccessControlService.editors());
        List<Section> sections = sectionRepository.findByChapterIdOrderByPositionAsc(chapterId);
        if (sections.size() != request.orderedIds().size()) {
            throw ApiException.badRequest("Reorder list must include every section exactly once");
        }
        Map<UUID, Section> byId = new HashMap<>();
        for (Section section : sections) {
            byId.put(section.getId(), section);
        }
        Set<UUID> seen = new HashSet<>();
        int offset = sections.size() + 1000;
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).setPosition(offset + i);
        }
        sectionRepository.flush();
        for (int i = 0; i < request.orderedIds().size(); i++) {
            UUID id = request.orderedIds().get(i);
            Section section = byId.get(id);
            if (section == null || !seen.add(id)) {
                throw ApiException.badRequest("Invalid section id in reorder list");
            }
            section.setPosition(i);
        }
        return sectionRepository.findByChapterIdOrderByPositionAsc(chapterId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Section requireSection(UUID sectionId) {
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> ApiException.notFound("Section not found"));
    }

    private SectionResponse toResponse(Section section) {
        return new SectionResponse(
                section.getId(),
                section.getChapter().getId(),
                section.getTitle(),
                section.getPosition(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }
}
