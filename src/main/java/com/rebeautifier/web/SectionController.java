package com.rebeautifier.web;

import com.rebeautifier.service.SectionService;
import com.rebeautifier.web.dto.ReorderRequest;
import com.rebeautifier.web.dto.SectionCreateRequest;
import com.rebeautifier.web.dto.SectionResponse;
import com.rebeautifier.web.dto.SectionUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class SectionController {

    private final SectionService sectionService;

    public SectionController(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @GetMapping("/api/v1/chapters/{chapterId}/sections")
    public List<SectionResponse> list(@PathVariable UUID chapterId) {
        return sectionService.list(chapterId);
    }

    @PostMapping("/api/v1/chapters/{chapterId}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionResponse create(@PathVariable UUID chapterId, @Valid @RequestBody SectionCreateRequest request) {
        return sectionService.create(chapterId, request);
    }

    @PutMapping("/api/v1/chapters/{chapterId}/sections/reorder")
    public List<SectionResponse> reorder(@PathVariable UUID chapterId, @Valid @RequestBody ReorderRequest request) {
        return sectionService.reorder(chapterId, request);
    }

    @PatchMapping("/api/v1/sections/{sectionId}")
    public SectionResponse update(@PathVariable UUID sectionId, @Valid @RequestBody SectionUpdateRequest request) {
        return sectionService.update(sectionId, request);
    }

    @DeleteMapping("/api/v1/sections/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID sectionId) {
        sectionService.delete(sectionId);
    }
}
