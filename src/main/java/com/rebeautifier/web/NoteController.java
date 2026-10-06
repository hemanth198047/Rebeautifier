package com.rebeautifier.web;

import com.rebeautifier.domain.NoteAttachType;
import com.rebeautifier.service.NoteService;
import com.rebeautifier.web.dto.NoteCreateRequest;
import com.rebeautifier.web.dto.NoteResponse;
import com.rebeautifier.web.dto.NoteUpdateRequest;
import com.rebeautifier.web.dto.ReorderRequest;
import com.rebeautifier.web.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/books/{bookId}/notes")
    public List<NoteResponse> list(@PathVariable UUID bookId,
                                   @RequestParam(required = false) UUID chapterId,
                                   @RequestParam(required = false) UUID sectionId) {
        return noteService.list(bookId, chapterId, sectionId);
    }

    @PostMapping("/books/{bookId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@PathVariable UUID bookId, @Valid @RequestBody NoteCreateRequest request) {
        return noteService.create(bookId, request);
    }

    @GetMapping("/notes/{noteId}")
    public NoteResponse get(@PathVariable UUID noteId) {
        return noteService.get(noteId);
    }

    @PatchMapping("/notes/{noteId}")
    public NoteResponse update(@PathVariable UUID noteId, @Valid @RequestBody NoteUpdateRequest request) {
        return noteService.update(noteId, request);
    }

    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID noteId) {
        noteService.delete(noteId);
    }

    @PutMapping("/notes/reorder")
    public List<NoteResponse> reorder(@Valid @RequestBody NoteReorderBody body) {
        if (body.bookId() == null || body.attachType() == null) {
            throw ApiException.badRequest("bookId and attachType are required");
        }
        if (body.orderedIds() == null || body.orderedIds().isEmpty()) {
            throw ApiException.badRequest("orderedIds is required");
        }
        return noteService.reorder(new ReorderRequest(body.orderedIds()), body.bookId(), body.attachType(),
                body.chapterId(), body.sectionId());
    }

    public record NoteReorderBody(
            UUID bookId,
            NoteAttachType attachType,
            UUID chapterId,
            UUID sectionId,
            List<UUID> orderedIds
    ) {
    }
}
