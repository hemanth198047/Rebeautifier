package com.rebeautifier.service;

import com.rebeautifier.domain.Book;
import com.rebeautifier.domain.BookMember;
import com.rebeautifier.domain.BookRole;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.BookMemberRepository;
import com.rebeautifier.repository.BookRepository;
import com.rebeautifier.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AccessControlService {

    private final BookRepository bookRepository;
    private final BookMemberRepository bookMemberRepository;

    public AccessControlService(BookRepository bookRepository, BookMemberRepository bookMemberRepository) {
        this.bookRepository = bookRepository;
        this.bookMemberRepository = bookMemberRepository;
    }

    @Transactional(readOnly = true)
    public Book requireBook(UUID bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> ApiException.notFound("Book not found"));
    }

    @Transactional(readOnly = true)
    public BookRole requireRole(UUID bookId, User user, Set<BookRole> allowed) {
        BookRole role = resolveRole(bookId, user)
                .orElseThrow(() -> ApiException.forbidden("You do not have access to this book"));
        if (!allowed.contains(role)) {
            throw ApiException.forbidden("Insufficient permissions for this book");
        }
        return role;
    }

    @Transactional(readOnly = true)
    public Optional<BookRole> resolveRole(UUID bookId, User user) {
        Book book = requireBook(bookId);
        if (book.getOwner().getId().equals(user.getId())) {
            return Optional.of(BookRole.OWNER);
        }
        return bookMemberRepository.findByBookIdAndUserId(bookId, user.getId())
                .map(BookMember::getRole);
    }

    public static Set<BookRole> readers() {
        return EnumSet.of(BookRole.OWNER, BookRole.EDITOR, BookRole.VIEWER);
    }

    public static Set<BookRole> editors() {
        return EnumSet.of(BookRole.OWNER, BookRole.EDITOR);
    }

    public static Set<BookRole> owners() {
        return EnumSet.of(BookRole.OWNER);
    }
}
