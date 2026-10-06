package com.rebeautifier.repository;

import com.rebeautifier.domain.NoteRevision;
import com.rebeautifier.domain.RevisionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRevisionRepository extends JpaRepository<NoteRevision, UUID> {

    @Query("""
            SELECT r FROM NoteRevision r
            JOIN FETCH r.note n
            JOIN FETCH n.book
            JOIN FETCH n.createdBy
            JOIN FETCH r.createdBy
            WHERE r.note.id = :noteId
            ORDER BY r.createdAt DESC
            """)
    List<NoteRevision> findByNoteIdOrderByCreatedAtDesc(@Param("noteId") UUID noteId);

    @Query("""
            SELECT r FROM NoteRevision r
            JOIN FETCH r.note n
            JOIN FETCH n.book
            JOIN FETCH n.createdBy
            JOIN FETCH r.createdBy
            WHERE r.id = :id
            """)
    Optional<NoteRevision> findDetailedById(@Param("id") UUID id);

    long countByStatus(RevisionStatus status);
}
