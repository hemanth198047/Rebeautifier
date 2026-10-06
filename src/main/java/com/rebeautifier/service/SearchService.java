package com.rebeautifier.service;

import com.rebeautifier.domain.Book;
import com.rebeautifier.domain.Chapter;
import com.rebeautifier.domain.Section;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.BookRepository;
import com.rebeautifier.repository.ChapterRepository;
import com.rebeautifier.repository.SectionRepository;
import com.rebeautifier.web.dto.SearchResponse;
import com.rebeautifier.web.error.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final int MAX_HITS = 60;

    private final AuthService authService;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final SectionRepository sectionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public SearchService(AuthService authService,
                         BookRepository bookRepository,
                         ChapterRepository chapterRepository,
                         SectionRepository sectionRepository) {
        this.authService = authService;
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String rawQuery) {
        User user = authService.requireCurrentUser();
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2) {
            throw ApiException.badRequest("Search query must be at least 2 characters");
        }
        if (query.length() > 120) {
            throw ApiException.badRequest("Search query is too long");
        }

        List<Book> accessible = bookRepository.findAccessibleByUser(user.getId());
        if (accessible.isEmpty()) {
            return new SearchResponse(query, List.of());
        }
        Map<UUID, Book> booksById = accessible.stream()
                .collect(Collectors.toMap(Book::getId, b -> b, (a, b) -> a, LinkedHashMap::new));
        Set<UUID> bookIds = booksById.keySet();
        String needle = query.toLowerCase(Locale.ROOT);
        String like = "%" + escapeLike(needle) + "%";

        Map<String, RankedHit> hits = new LinkedHashMap<>();

        collectBooks(hits, booksById, bookIds, query, like, needle);
        collectChapters(hits, booksById, bookIds, query, like);
        collectSections(hits, booksById, bookIds, query, like);
        collectNotes(hits, booksById, bookIds, query, like, needle);

        List<SearchResponse.SearchHit> ordered = hits.values().stream()
                .sorted(Comparator
                        .comparingDouble(RankedHit::rank).reversed()
                        .thenComparingInt(RankedHit::typeRank)
                        .thenComparing(h -> h.hit().title() == null ? "" : h.hit().title().toLowerCase(Locale.ROOT)))
                .limit(MAX_HITS)
                .map(RankedHit::hit)
                .toList();

        return new SearchResponse(query, ordered);
    }

    private void collectBooks(Map<String, RankedHit> hits, Map<UUID, Book> booksById, Set<UUID> bookIds,
                              String query, String like, String needle) {
        Query bookQuery = entityManager.createNativeQuery("""
                SELECT b.id::text,
                       ts_rank(
                         to_tsvector('english', coalesce(b.title,'') || ' ' || coalesce(b.description,'')),
                         plainto_tsquery('english', :q)
                       ) AS rank
                FROM books b
                WHERE b.id IN (:bookIds)
                  AND (
                    to_tsvector('english', coalesce(b.title,'') || ' ' || coalesce(b.description,''))
                        @@ plainto_tsquery('english', :q)
                    OR lower(b.title) LIKE :like ESCAPE '\\'
                    OR lower(coalesce(b.description,'')) LIKE :like ESCAPE '\\'
                  )
                """);
        bookQuery.setParameter("q", query);
        bookQuery.setParameter("like", like);
        bookQuery.setParameter("bookIds", bookIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bookQuery.getResultList();
        for (Object[] row : rows) {
            UUID id = UUID.fromString((String) row[0]);
            Book book = booksById.get(id);
            if (book == null) continue;
            double rank = row[1] != null ? ((Number) row[1]).doubleValue() : 0;
            put(hits, "BOOK:" + id, rank, "BOOK", new SearchResponse.SearchHit(
                    "BOOK", id, id, book.getTitle(),
                    null, null, null, null,
                    book.getTitle(),
                    snippet(book.getDescription(), needle),
                    "/books/" + id
            ));
        }
    }

    private void collectChapters(Map<String, RankedHit> hits, Map<UUID, Book> booksById, Set<UUID> bookIds,
                                 String query, String like) {
        Query chapterQuery = entityManager.createNativeQuery("""
                SELECT c.id::text, c.book_id::text, c.title,
                       ts_rank(to_tsvector('english', coalesce(c.title,'')), plainto_tsquery('english', :q)) AS rank
                FROM chapters c
                WHERE c.book_id IN (:bookIds)
                  AND (
                    to_tsvector('english', coalesce(c.title,'')) @@ plainto_tsquery('english', :q)
                    OR lower(c.title) LIKE :like ESCAPE '\\'
                  )
                """);
        chapterQuery.setParameter("q", query);
        chapterQuery.setParameter("like", like);
        chapterQuery.setParameter("bookIds", bookIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = chapterQuery.getResultList();
        for (Object[] row : rows) {
            UUID chapterId = UUID.fromString((String) row[0]);
            UUID bookId = UUID.fromString((String) row[1]);
            String title = (String) row[2];
            Book book = booksById.get(bookId);
            if (book == null) continue;
            double rank = row[3] != null ? ((Number) row[3]).doubleValue() : 0;
            put(hits, "CHAPTER:" + chapterId, rank, "CHAPTER", new SearchResponse.SearchHit(
                    "CHAPTER", chapterId, bookId, book.getTitle(),
                    chapterId, title, null, null,
                    title, null,
                    "/books/" + bookId + "/chapters/" + chapterId
            ));
        }
    }

    private void collectSections(Map<String, RankedHit> hits, Map<UUID, Book> booksById, Set<UUID> bookIds,
                                 String query, String like) {
        Query sectionQuery = entityManager.createNativeQuery("""
                SELECT s.id::text, c.book_id::text, c.id::text, c.title, s.title,
                       ts_rank(to_tsvector('english', coalesce(s.title,'')), plainto_tsquery('english', :q)) AS rank
                FROM sections s
                JOIN chapters c ON c.id = s.chapter_id
                WHERE c.book_id IN (:bookIds)
                  AND (
                    to_tsvector('english', coalesce(s.title,'')) @@ plainto_tsquery('english', :q)
                    OR lower(s.title) LIKE :like ESCAPE '\\'
                  )
                """);
        sectionQuery.setParameter("q", query);
        sectionQuery.setParameter("like", like);
        sectionQuery.setParameter("bookIds", bookIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = sectionQuery.getResultList();
        for (Object[] row : rows) {
            UUID sectionId = UUID.fromString((String) row[0]);
            UUID bookId = UUID.fromString((String) row[1]);
            UUID chapterId = UUID.fromString((String) row[2]);
            String chapterTitle = (String) row[3];
            String sectionTitle = (String) row[4];
            Book book = booksById.get(bookId);
            if (book == null) continue;
            double rank = row[5] != null ? ((Number) row[5]).doubleValue() : 0;
            put(hits, "SECTION:" + sectionId, rank, "SECTION", new SearchResponse.SearchHit(
                    "SECTION", sectionId, bookId, book.getTitle(),
                    chapterId, chapterTitle, sectionId, sectionTitle,
                    sectionTitle, null,
                    "/books/" + bookId + "/chapters/" + chapterId + "/sections/" + sectionId
            ));
        }
    }

    private void collectNotes(Map<String, RankedHit> hits, Map<UUID, Book> booksById, Set<UUID> bookIds,
                              String query, String like, String needle) {
        Query noteQuery = entityManager.createNativeQuery("""
                SELECT n.id::text, n.book_id::text, n.chapter_id::text, n.section_id::text,
                       n.title, n.body_plain,
                       ts_rank(
                         to_tsvector('english', coalesce(n.title,'') || ' ' || coalesce(n.body_plain,'') || ' ' || coalesce(n.tags,'')),
                         plainto_tsquery('english', :q)
                       ) AS rank
                FROM notes n
                WHERE n.book_id IN (:bookIds)
                  AND (
                    to_tsvector('english', coalesce(n.title,'') || ' ' || coalesce(n.body_plain,'') || ' ' || coalesce(n.tags,''))
                        @@ plainto_tsquery('english', :q)
                    OR lower(coalesce(n.title,'')) LIKE :like ESCAPE '\\'
                    OR lower(coalesce(n.body_plain,'')) LIKE :like ESCAPE '\\'
                    OR lower(coalesce(n.tags,'')) LIKE :like ESCAPE '\\'
                  )
                """);
        noteQuery.setParameter("q", query);
        noteQuery.setParameter("like", like);
        noteQuery.setParameter("bookIds", bookIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = noteQuery.getResultList();

        Map<UUID, Chapter> chapterCache = new HashMap<>();
        Map<UUID, Section> sectionCache = new HashMap<>();
        for (Object[] row : rows) {
            UUID noteId = UUID.fromString((String) row[0]);
            UUID bookId = UUID.fromString((String) row[1]);
            UUID chapterId = row[2] != null ? UUID.fromString((String) row[2]) : null;
            UUID sectionId = row[3] != null ? UUID.fromString((String) row[3]) : null;
            String title = (String) row[4];
            String bodyPlain = (String) row[5];
            Book book = booksById.get(bookId);
            if (book == null) continue;
            double rank = row[6] != null ? ((Number) row[6]).doubleValue() : 0;
            String chapterTitle = null;
            String sectionTitle = null;
            if (chapterId != null) {
                Chapter chapter = chapterCache.computeIfAbsent(chapterId,
                        id -> chapterRepository.findById(id).orElse(null));
                if (chapter != null) {
                    chapterTitle = chapter.getTitle();
                }
            }
            if (sectionId != null) {
                Section section = sectionCache.computeIfAbsent(sectionId,
                        id -> sectionRepository.findById(id).orElse(null));
                if (section != null) {
                    sectionTitle = section.getTitle();
                }
            }
            put(hits, "NOTE:" + noteId, rank, "NOTE", new SearchResponse.SearchHit(
                    "NOTE", noteId, bookId, book.getTitle(),
                    chapterId, chapterTitle, sectionId, sectionTitle,
                    title != null ? title : "Untitled note",
                    snippet(bodyPlain, needle),
                    "/books/" + bookId + "/notes/" + noteId
            ));
        }
    }

    private static void put(Map<String, RankedHit> hits, String key, double rank, String type,
                            SearchResponse.SearchHit hit) {
        RankedHit existing = hits.get(key);
        if (existing == null || rank > existing.rank()) {
            hits.put(key, new RankedHit(rank, typeRank(type), hit));
        }
    }

    private record RankedHit(double rank, int typeRank, SearchResponse.SearchHit hit) {
    }

    private static int typeRank(String type) {
        return switch (type) {
            case "BOOK" -> 0;
            case "CHAPTER" -> 1;
            case "SECTION" -> 2;
            default -> 3;
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String snippet(String text, String needle) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(needle);
        if (idx < 0) {
            return text.length() > 140 ? text.substring(0, 140) + "…" : text;
        }
        int start = Math.max(0, idx - 40);
        int end = Math.min(text.length(), idx + needle.length() + 80);
        String piece = text.substring(start, end).trim();
        return (start > 0 ? "…" : "") + piece + (end < text.length() ? "…" : "");
    }
}
