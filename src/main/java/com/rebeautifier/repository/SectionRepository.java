package com.rebeautifier.repository;

import com.rebeautifier.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {
    List<Section> findByChapterIdOrderByPositionAsc(UUID chapterId);

    int countByChapterId(UUID chapterId);
}
