package com.rebeautifier.repository;

import com.rebeautifier.domain.Note;
import com.rebeautifier.domain.NoteAttachType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByBookIdAndAttachTypeOrderByPositionAsc(UUID bookId, NoteAttachType attachType);

    List<Note> findByChapterIdAndAttachTypeOrderByPositionAsc(UUID chapterId, NoteAttachType attachType);

    List<Note> findBySectionIdAndAttachTypeOrderByPositionAsc(UUID sectionId, NoteAttachType attachType);

    @Query("""
            SELECT n FROM Note n
            WHERE n.book.id = :bookId
              AND (:chapterId IS NULL OR n.chapter.id = :chapterId)
              AND (:sectionId IS NULL OR n.section.id = :sectionId)
            ORDER BY n.position ASC, n.createdAt ASC
            """)
    List<Note> findFiltered(@Param("bookId") UUID bookId,
                            @Param("chapterId") UUID chapterId,
                            @Param("sectionId") UUID sectionId);

    @Query("""
            SELECT n FROM Note n
            JOIN FETCH n.book
            JOIN FETCH n.createdBy
            LEFT JOIN FETCH n.chapter
            LEFT JOIN FETCH n.section
            WHERE n.id = :id
            """)
    Optional<Note> findDetailedById(@Param("id") UUID id);

    long countByBookIdAndAttachType(UUID bookId, NoteAttachType attachType);

    long countByChapterIdAndAttachType(UUID chapterId, NoteAttachType attachType);

    long countBySectionIdAndAttachType(UUID sectionId, NoteAttachType attachType);

    long countByBookId(UUID bookId);
}
