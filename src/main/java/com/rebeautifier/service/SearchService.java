package com.rebeautifier.service;

import com.rebeautifier.domain.Book;
import com.rebeautifier.domain.Chapter;
import com.rebeautifier.domain.Note;
import com.rebeautifier.domain.Section;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.BookRepository;
import com.rebeautifier.repository.ChapterRepository;
import com.rebeautifier.repository.NoteRepository;
import com.rebeautifier.repository.SectionRepository;
import com.rebeautifier.web.dto.SearchResponse;
import com.rebeautifier.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final NoteRepository noteRepository;

    public SearchService(AuthService authService,
                         BookRepository bookRepository,
                         ChapterRepository chapterRepository,
                         SectionRepository sectionRepository,
                         NoteRepository noteRepository) {
        this.authService = authService;
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.noteRepository = noteRepository;
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
        Set<UUID> bookIds = accessible.stream().map(Book::getId).collect(Collectors.toSet());
        String needle = query.toLowerCase(Locale.ROOT);

        List<SearchResponse.SearchHit> hits = new ArrayList<>();

        for (Book book : accessible) {
            if (contains(book.getTitle(), needle) || contains(book.getDescription(), needle)) {
                hits.add(new SearchResponse.SearchHit(
                        "BOOK",
                        book.getId(),
                        book.getId(),
                        book.getTitle(),
                        null, null, null, null,
                        book.getTitle(),
                        snippet(book.getDescription(), needle),
                        "/books/" + book.getId()
                ));
            }
        }

        for (UUID bookId : bookIds) {
            Book book = accessible.stream().filter(b -> b.getId().equals(bookId)).findFirst().orElse(null);
            if (book == null) continue;
            List<Chapter> chapters = chapterRepository.findByBookIdOrderByPositionAsc(bookId);
            for (Chapter chapter : chapters) {
                if (contains(chapter.getTitle(), needle)) {
                    hits.add(new SearchResponse.SearchHit(
                            "CHAPTER",
                            chapter.getId(),
                            bookId,
                            book.getTitle(),
                            chapter.getId(),
                            chapter.getTitle(),
                            null, null,
                            chapter.getTitle(),
                            null,
                            "/books/" + bookId + "/chapters/" + chapter.getId()
                    ));
                }
                List<Section> sections = sectionRepository.findByChapterIdOrderByPositionAsc(chapter.getId());
                for (Section section : sections) {
                    if (contains(section.getTitle(), needle)) {
                        hits.add(new SearchResponse.SearchHit(
                                "SECTION",
                                section.getId(),
                                bookId,
                                book.getTitle(),
                                chapter.getId(),
                                chapter.getTitle(),
                                section.getId(),
                                section.getTitle(),
                                section.getTitle(),
                                null,
                                "/books/" + bookId + "/chapters/" + chapter.getId() + "/sections/" + section.getId()
                        ));
                    }
                }
            }

            for (Note note : noteRepository.findFiltered(bookId, null, null)) {
                boolean matchTitle = contains(note.getTitle(), needle);
                boolean matchBody = contains(note.getBodyPlain(), needle);
                boolean matchTags = note.getTags() != null && note.getTags().toLowerCase(Locale.ROOT).contains(needle);
                if (!matchTitle && !matchBody && !matchTags) {
                    continue;
                }
                String href = "/books/" + bookId + "/notes/" + note.getId();
                String chapterTitle = note.getChapter() != null ? note.getChapter().getTitle() : null;
                String sectionTitle = note.getSection() != null ? note.getSection().getTitle() : null;
                hits.add(new SearchResponse.SearchHit(
                        "NOTE",
                        note.getId(),
                        bookId,
                        book.getTitle(),
                        note.getChapter() != null ? note.getChapter().getId() : null,
                        chapterTitle,
                        note.getSection() != null ? note.getSection().getId() : null,
                        sectionTitle,
                        note.getTitle() != null ? note.getTitle() : "Untitled note",
                        snippet(note.getBodyPlain(), needle),
                        href
                ));
            }
        }

        hits.sort(Comparator
                .comparing((SearchResponse.SearchHit h) -> typeRank(h.type()))
                .thenComparing(h -> h.title() == null ? "" : h.title().toLowerCase(Locale.ROOT)));

        if (hits.size() > MAX_HITS) {
            hits = hits.subList(0, MAX_HITS);
        }
        return new SearchResponse(query, List.copyOf(hits));
    }

    private static int typeRank(String type) {
        return switch (type) {
            case "BOOK" -> 0;
            case "CHAPTER" -> 1;
            case "SECTION" -> 2;
            default -> 3;
        };
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
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
