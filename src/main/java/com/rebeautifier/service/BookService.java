package com.rebeautifier.service;

import com.rebeautifier.domain.Book;
import com.rebeautifier.domain.BookMember;
import com.rebeautifier.domain.BookRole;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.BookMemberRepository;
import com.rebeautifier.repository.BookRepository;
import com.rebeautifier.repository.UserRepository;
import com.rebeautifier.web.dto.BookCreateRequest;
import com.rebeautifier.web.dto.BookResponse;
import com.rebeautifier.web.dto.BookUpdateRequest;
import com.rebeautifier.web.dto.MemberAddRequest;
import com.rebeautifier.web.dto.MemberResponse;
import com.rebeautifier.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookMemberRepository bookMemberRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final CoverStorageService coverStorageService;
    private final AuthService authService;

    public BookService(BookRepository bookRepository,
                       BookMemberRepository bookMemberRepository,
                       UserRepository userRepository,
                       AccessControlService accessControlService,
                       CoverStorageService coverStorageService,
                       AuthService authService) {
        this.bookRepository = bookRepository;
        this.bookMemberRepository = bookMemberRepository;
        this.userRepository = userRepository;
        this.accessControlService = accessControlService;
        this.coverStorageService = coverStorageService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<BookResponse> listMine() {
        User user = authService.requireCurrentUser();
        return bookRepository.findAccessibleByUser(user.getId()).stream()
                .map(book -> toResponse(book, user))
                .toList();
    }

    @Transactional
    public BookResponse create(BookCreateRequest request) {
        User user = authService.requireCurrentUser();
        Book book = new Book();
        book.setOwner(user);
        book.setTitle(request.title().trim());
        book.setDescription(blankToNull(request.description()));
        bookRepository.save(book);

        BookMember ownerMembership = new BookMember();
        ownerMembership.setBook(book);
        ownerMembership.setUser(user);
        ownerMembership.setRole(BookRole.OWNER);
        bookMemberRepository.save(ownerMembership);

        return toResponse(book, user);
    }

    @Transactional(readOnly = true)
    public BookResponse get(UUID bookId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.readers());
        return toResponse(accessControlService.requireBook(bookId), user);
    }

    @Transactional
    public BookResponse update(UUID bookId, BookUpdateRequest request) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());
        Book book = accessControlService.requireBook(bookId);
        if (request.title() != null && !request.title().isBlank()) {
            book.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            book.setDescription(blankToNull(request.description()));
        }
        return toResponse(book, user);
    }

    @Transactional
    public void delete(UUID bookId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.owners());
        Book book = accessControlService.requireBook(bookId);
        coverStorageService.deleteIfPresent(book.getCoverStorageKey());
        bookMemberRepository.deleteByBookId(bookId);
        bookRepository.delete(book);
    }

    @Transactional
    public BookResponse uploadCover(UUID bookId, MultipartFile file) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());
        Book book = accessControlService.requireBook(bookId);
        coverStorageService.deleteIfPresent(book.getCoverStorageKey());
        CoverStorageService.StoredCover stored = coverStorageService.store(bookId, file);
        book.setCoverStorageKey(stored.storageKey());
        book.setCoverMime(stored.mimeType());
        return toResponse(book, user);
    }

    @Transactional
    public BookResponse removeCover(UUID bookId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.editors());
        Book book = accessControlService.requireBook(bookId);
        coverStorageService.deleteIfPresent(book.getCoverStorageKey());
        book.setCoverStorageKey(null);
        book.setCoverMime(null);
        return toResponse(book, user);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID bookId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.readers());
        return bookMemberRepository.findByBookIdOrderByInvitedAtAsc(bookId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public MemberResponse addMember(UUID bookId, MemberAddRequest request) {
        User actor = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, actor, AccessControlService.owners());
        if (request.role() == BookRole.OWNER) {
            throw ApiException.badRequest("Cannot assign OWNER via invite; use ownership transfer instead");
        }
        User invitee = userRepository.findByUsernameIgnoreCase(request.usernameOrEmail())
                .or(() -> userRepository.findByEmailIgnoreCase(request.usernameOrEmail()))
                .orElseThrow(() -> ApiException.notFound("User not found"));
        Book book = accessControlService.requireBook(bookId);
        if (book.getOwner().getId().equals(invitee.getId())) {
            throw ApiException.conflict("Owner is already a member");
        }
        if (bookMemberRepository.findByBookIdAndUserId(bookId, invitee.getId()).isPresent()) {
            throw ApiException.conflict("User is already a member of this book");
        }
        BookMember member = new BookMember();
        member.setBook(book);
        member.setUser(invitee);
        member.setRole(request.role());
        bookMemberRepository.save(member);
        return toMemberResponse(member);
    }

    @Transactional
    public MemberResponse updateMemberRole(UUID bookId, UUID userId, BookRole role) {
        User actor = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, actor, AccessControlService.owners());
        if (role == BookRole.OWNER) {
            throw ApiException.badRequest("Cannot promote to OWNER via this endpoint");
        }
        BookMember member = bookMemberRepository.findByBookIdAndUserId(bookId, userId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (member.getRole() == BookRole.OWNER) {
            throw ApiException.badRequest("Cannot change the owner membership role");
        }
        member.setRole(role);
        return toMemberResponse(member);
    }

    @Transactional
    public void removeMember(UUID bookId, UUID userId) {
        User actor = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, actor, AccessControlService.owners());
        BookMember member = bookMemberRepository.findByBookIdAndUserId(bookId, userId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (member.getRole() == BookRole.OWNER) {
            throw ApiException.badRequest("Cannot remove the owner");
        }
        bookMemberRepository.delete(member);
    }

    /**
     * Transfer book ownership to another existing user. Previous owner becomes EDITOR.
     */
    @Transactional
    public BookResponse transferOwnership(UUID bookId, String usernameOrEmail) {
        User actor = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, actor, AccessControlService.owners());
        Book book = accessControlService.requireBook(bookId);
        User nextOwner = userRepository.findByUsernameIgnoreCase(usernameOrEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(usernameOrEmail))
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (nextOwner.getId().equals(actor.getId())) {
            throw ApiException.badRequest("You already own this book");
        }

        BookMember previousOwnerMembership = bookMemberRepository.findByBookIdAndUserId(bookId, actor.getId())
                .orElseThrow(() -> ApiException.notFound("Owner membership not found"));
        previousOwnerMembership.setRole(BookRole.EDITOR);

        BookMember nextMembership = bookMemberRepository.findByBookIdAndUserId(bookId, nextOwner.getId())
                .orElse(null);
        if (nextMembership == null) {
            nextMembership = new BookMember();
            nextMembership.setBook(book);
            nextMembership.setUser(nextOwner);
            nextMembership.setRole(BookRole.OWNER);
            bookMemberRepository.save(nextMembership);
        } else {
            nextMembership.setRole(BookRole.OWNER);
        }

        book.setOwner(nextOwner);
        bookRepository.save(book);
        return toResponse(book, actor);
    }

    private BookResponse toResponse(Book book, User viewer) {
        BookRole role = accessControlService.resolveRole(book.getId(), viewer)
                .orElse(BookRole.VIEWER);
        String coverUrl = book.getCoverStorageKey() == null
                ? null
                : "/api/v1/covers/" + book.getCoverStorageKey();
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getDescription(),
                coverUrl,
                book.getOwner().getId(),
                book.getOwner().getDisplayName(),
                role,
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }

    private MemberResponse toMemberResponse(BookMember member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getInvitedAt(),
                member.getAcceptedAt()
        );
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
