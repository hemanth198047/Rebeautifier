package com.rebeautifier.web;

import com.rebeautifier.service.RefinementService;
import com.rebeautifier.web.dto.AcceptRevisionRequest;
import com.rebeautifier.web.dto.AiStatusResponse;
import com.rebeautifier.web.dto.NoteResponse;
import com.rebeautifier.web.dto.NoteRevisionResponse;
import com.rebeautifier.web.dto.RefineRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RefinementController {

    private final RefinementService refinementService;

    public RefinementController(RefinementService refinementService) {
        this.refinementService = refinementService;
    }

    @GetMapping("/system/ai-status")
    public AiStatusResponse aiStatus() {
        return refinementService.status();
    }

    @GetMapping("/notes/{noteId}/refinements")
    public List<NoteRevisionResponse> list(@PathVariable UUID noteId) {
        return refinementService.listForNote(noteId);
    }

    @PostMapping("/notes/{noteId}/refinements")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteRevisionResponse refine(@PathVariable UUID noteId, @Valid @RequestBody RefineRequest request) {
        return refinementService.refine(noteId, request);
    }

    @GetMapping("/refinements/{revisionId}")
    public NoteRevisionResponse get(@PathVariable UUID revisionId) {
        return refinementService.get(revisionId);
    }

    @PostMapping("/refinements/{revisionId}/accept")
    public NoteResponse accept(@PathVariable UUID revisionId,
                               @RequestBody(required = false) AcceptRevisionRequest request) {
        return refinementService.accept(revisionId, request == null ? new AcceptRevisionRequest(null) : request);
    }

    @PostMapping("/refinements/{revisionId}/reject")
    public NoteRevisionResponse reject(@PathVariable UUID revisionId) {
        return refinementService.reject(revisionId);
    }

    @PostMapping("/refinements/{revisionId}/regenerate")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteRevisionResponse regenerate(@PathVariable UUID revisionId) {
        return refinementService.regenerate(revisionId);
    }
}
