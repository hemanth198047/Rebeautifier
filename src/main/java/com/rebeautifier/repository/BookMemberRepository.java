package com.rebeautifier.repository;

import com.rebeautifier.domain.BookMember;
import com.rebeautifier.domain.BookRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookMemberRepository extends JpaRepository<BookMember, UUID> {
    List<BookMember> findByBookIdOrderByInvitedAtAsc(UUID bookId);

    Optional<BookMember> findByBookIdAndUserId(UUID bookId, UUID userId);

    boolean existsByBookIdAndUserIdAndRole(UUID bookId, UUID userId, BookRole role);

    void deleteByBookId(UUID bookId);
}
