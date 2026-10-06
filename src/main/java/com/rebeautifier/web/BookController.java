package com.rebeautifier.web;

import com.rebeautifier.domain.BookRole;
import com.rebeautifier.service.BookService;
import com.rebeautifier.web.dto.BookCreateRequest;
import com.rebeautifier.web.dto.BookResponse;
import com.rebeautifier.web.dto.BookUpdateRequest;
import com.rebeautifier.web.dto.MemberAddRequest;
import com.rebeautifier.web.dto.MemberResponse;
import com.rebeautifier.web.dto.TransferOwnershipRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public List<BookResponse> list() {
        return bookService.listMine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(@Valid @RequestBody BookCreateRequest request) {
        return bookService.create(request);
    }

    @GetMapping("/{bookId}")
    public BookResponse get(@PathVariable UUID bookId) {
        return bookService.get(bookId);
    }

    @PatchMapping("/{bookId}")
    public BookResponse update(@PathVariable UUID bookId, @Valid @RequestBody BookUpdateRequest request) {
        return bookService.update(bookId, request);
    }

    @DeleteMapping("/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID bookId) {
        bookService.delete(bookId);
    }

    @PutMapping(value = "/{bookId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookResponse uploadCover(@PathVariable UUID bookId, @RequestPart("file") MultipartFile file) {
        return bookService.uploadCover(bookId, file);
    }

    @DeleteMapping("/{bookId}/cover")
    public BookResponse removeCover(@PathVariable UUID bookId) {
        return bookService.removeCover(bookId);
    }

    @GetMapping("/{bookId}/members")
    public List<MemberResponse> members(@PathVariable UUID bookId) {
        return bookService.listMembers(bookId);
    }

    @PostMapping("/{bookId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@PathVariable UUID bookId, @Valid @RequestBody MemberAddRequest request) {
        return bookService.addMember(bookId, request);
    }

    @PatchMapping("/{bookId}/members/{userId}")
    public MemberResponse updateMember(@PathVariable UUID bookId,
                                       @PathVariable UUID userId,
                                       @RequestBody Map<String, BookRole> body) {
        BookRole role = body.get("role");
        if (role == null) {
            throw com.rebeautifier.web.error.ApiException.badRequest("role is required");
        }
        return bookService.updateMemberRole(bookId, userId, role);
    }

    @DeleteMapping("/{bookId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID bookId, @PathVariable UUID userId) {
        bookService.removeMember(bookId, userId);
    }

    @PostMapping("/{bookId}/transfer")
    public BookResponse transferOwnership(@PathVariable UUID bookId,
                                          @Valid @RequestBody TransferOwnershipRequest request) {
        return bookService.transferOwnership(bookId, request.usernameOrEmail());
    }
}
