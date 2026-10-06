package com.rebeautifier.repository;

import com.rebeautifier.domain.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
    List<Chapter> findByBookIdOrderByPositionAsc(UUID bookId);

    int countByBookId(UUID bookId);

    void deleteByBookId(UUID bookId);
}
