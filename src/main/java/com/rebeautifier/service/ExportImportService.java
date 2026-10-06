package com.rebeautifier.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebeautifier.domain.Book;
import com.rebeautifier.domain.BookMember;
import com.rebeautifier.domain.BookRole;
import com.rebeautifier.domain.Chapter;
import com.rebeautifier.domain.Note;
import com.rebeautifier.domain.NoteAttachType;
import com.rebeautifier.domain.NoteStatus;
import com.rebeautifier.domain.Section;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.BookMemberRepository;
import com.rebeautifier.repository.BookRepository;
import com.rebeautifier.repository.ChapterRepository;
import com.rebeautifier.repository.NoteRepository;
import com.rebeautifier.repository.SectionRepository;
import com.rebeautifier.web.dto.BookBackupDocument;
import com.rebeautifier.web.dto.BookResponse;
import com.rebeautifier.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ExportImportService {

    private static final Pattern TAG_STRIP = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final AuthService authService;
    private final AccessControlService accessControlService;
    private final BookRepository bookRepository;
    private final BookMemberRepository bookMemberRepository;
    private final ChapterRepository chapterRepository;
    private final SectionRepository sectionRepository;
    private final NoteRepository noteRepository;
    private final BookService bookService;
    private final CoverStorageService coverStorageService;
    private final ObjectMapper objectMapper;

    public ExportImportService(AuthService authService,
                               AccessControlService accessControlService,
                               BookRepository bookRepository,
                               BookMemberRepository bookMemberRepository,
                               ChapterRepository chapterRepository,
                               SectionRepository sectionRepository,
                               NoteRepository noteRepository,
                               BookService bookService,
                               CoverStorageService coverStorageService,
                               ObjectMapper objectMapper) {
        this.authService = authService;
        this.accessControlService = accessControlService;
        this.bookRepository = bookRepository;
        this.bookMemberRepository = bookMemberRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.noteRepository = noteRepository;
        this.bookService = bookService;
        this.coverStorageService = coverStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public BookBackupDocument exportJson(UUID bookId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.readers());
        Book book = accessControlService.requireBook(bookId);
        return new BookBackupDocument(
                BookBackupDocument.SCHEMA,
                BookBackupDocument.VERSION,
                buildBookPayload(book)
        );
    }

    @Transactional(readOnly = true)
    public String exportMarkdown(UUID bookId) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.readers());
        Book book = accessControlService.requireBook(bookId);
        BookBackupDocument.BookPayload payload = buildBookPayload(book);
        StringBuilder md = new StringBuilder();
        md.append("# ").append(escapeMd(payload.title())).append("\n\n");
        if (payload.description() != null && !payload.description().isBlank()) {
            md.append(payload.description().trim()).append("\n\n");
        }
        if (payload.notes() != null) {
            for (BookBackupDocument.NotePayload note : payload.notes()) {
                appendNoteMarkdown(md, note, 2);
            }
        }
        if (payload.chapters() != null) {
            for (BookBackupDocument.ChapterPayload chapter : payload.chapters()) {
                md.append("## ").append(escapeMd(chapter.title())).append("\n\n");
                if (chapter.notes() != null) {
                    for (BookBackupDocument.NotePayload note : chapter.notes()) {
                        appendNoteMarkdown(md, note, 3);
                    }
                }
                if (chapter.sections() != null) {
                    for (BookBackupDocument.SectionPayload section : chapter.sections()) {
                        md.append("### ").append(escapeMd(section.title())).append("\n\n");
                        if (section.notes() != null) {
                            for (BookBackupDocument.NotePayload note : section.notes()) {
                                appendNoteMarkdown(md, note, 4);
                            }
                        }
                    }
                }
            }
        }
        return md.toString();
    }

    @Transactional
    public BookResponse importJson(String rawJson) {
        User user = authService.requireCurrentUser();
        BookBackupDocument document = parseAndValidate(rawJson);
        BookBackupDocument.BookPayload payload = document.book();

        Book book = new Book();
        book.setOwner(user);
        book.setTitle(requireTitle(payload.title(), "Book title"));
        book.setDescription(blankToNull(payload.description()));
        bookRepository.save(book);

        BookMember ownerMembership = new BookMember();
        ownerMembership.setBook(book);
        ownerMembership.setUser(user);
        ownerMembership.setRole(BookRole.OWNER);
        bookMemberRepository.save(ownerMembership);

        populateBookContent(user, book, payload);
        return bookService.get(book.getId());
    }

    /**
     * Replace hierarchy/content of an owned book while preserving memberships.
     */
    @Transactional
    public BookResponse replaceImportJson(UUID bookId, String rawJson) {
        User user = authService.requireCurrentUser();
        accessControlService.requireRole(bookId, user, AccessControlService.owners());
        Book book = accessControlService.requireBook(bookId);
        BookBackupDocument document = parseAndValidate(rawJson);
        BookBackupDocument.BookPayload payload = document.book();

        coverStorageService.deleteIfPresent(book.getCoverStorageKey());
        book.setCoverStorageKey(null);
        book.setCoverMime(null);
        noteRepository.deleteByBookId(bookId);
        chapterRepository.deleteByBookId(bookId);
        noteRepository.flush();
        chapterRepository.flush();

        book.setTitle(requireTitle(payload.title(), "Book title"));
        book.setDescription(blankToNull(payload.description()));
        bookRepository.saveAndFlush(book);

        populateBookContent(user, book, payload);
        return bookService.get(book.getId());
    }

    private void populateBookContent(User user, Book book, BookBackupDocument.BookPayload payload) {
        restoreCover(book, payload);

        if (payload.notes() != null) {
            int i = 0;
            for (BookBackupDocument.NotePayload notePayload : payload.notes()) {
                createNote(user, book, NoteAttachType.BOOK, null, null, notePayload, i++);
            }
        }

        if (payload.chapters() != null) {
            int chapterPos = 0;
            for (BookBackupDocument.ChapterPayload chapterPayload : payload.chapters()) {
                Chapter chapter = new Chapter();
                chapter.setBook(book);
                chapter.setTitle(requireTitle(chapterPayload.title(), "Chapter title"));
                chapter.setPosition(chapterPayload.position() >= 0 ? chapterPayload.position() : chapterPos);
                chapterRepository.save(chapter);
                chapterPos++;

                if (chapterPayload.notes() != null) {
                    int ni = 0;
                    for (BookBackupDocument.NotePayload notePayload : chapterPayload.notes()) {
                        createNote(user, book, NoteAttachType.CHAPTER, chapter, null, notePayload, ni++);
                    }
                }
                if (chapterPayload.sections() != null) {
                    int sectionPos = 0;
                    for (BookBackupDocument.SectionPayload sectionPayload : chapterPayload.sections()) {
                        Section section = new Section();
                        section.setChapter(chapter);
                        section.setTitle(requireTitle(sectionPayload.title(), "Section title"));
                        section.setPosition(sectionPayload.position() >= 0 ? sectionPayload.position() : sectionPos);
                        sectionRepository.save(section);
                        sectionPos++;
                        if (sectionPayload.notes() != null) {
                            int ni = 0;
                            for (BookBackupDocument.NotePayload notePayload : sectionPayload.notes()) {
                                createNote(user, book, NoteAttachType.SECTION, chapter, section, notePayload, ni++);
                            }
                        }
                    }
                }
            }
        }
    }

    private BookBackupDocument parseAndValidate(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw ApiException.badRequest("Import body is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!root.hasNonNull("schema") || !BookBackupDocument.SCHEMA.equals(root.get("schema").asText())) {
                throw new ApiException("IMPORT_INVALID",
                        "Invalid backup schema. Expected schema \"" + BookBackupDocument.SCHEMA + "\".",
                        HttpStatus.BAD_REQUEST);
            }
            if (!root.has("schemaVersion") || !root.get("schemaVersion").canConvertToInt()) {
                throw new ApiException("IMPORT_INVALID",
                        "Unsupported schemaVersion. Expected " + BookBackupDocument.MIN_SUPPORTED_VERSION
                                + "–" + BookBackupDocument.VERSION + ".",
                        HttpStatus.BAD_REQUEST);
            }
            int version = root.get("schemaVersion").asInt();
            if (version < BookBackupDocument.MIN_SUPPORTED_VERSION || version > BookBackupDocument.VERSION) {
                throw new ApiException("IMPORT_INVALID",
                        "Unsupported schemaVersion. Expected " + BookBackupDocument.MIN_SUPPORTED_VERSION
                                + "–" + BookBackupDocument.VERSION + ".",
                        HttpStatus.BAD_REQUEST);
            }
            if (!root.has("book") || !root.get("book").isObject()) {
                throw new ApiException("IMPORT_INVALID", "Backup must include a book object.", HttpStatus.BAD_REQUEST);
            }
            BookBackupDocument document = objectMapper.treeToValue(root, BookBackupDocument.class);
            if (document.book() == null || document.book().title() == null || document.book().title().isBlank()) {
                throw new ApiException("IMPORT_INVALID", "Book title is required.", HttpStatus.BAD_REQUEST);
            }
            validateNotes(document.book().notes(), "book");
            validateCover(document.book());
            if (document.book().chapters() != null) {
                for (BookBackupDocument.ChapterPayload chapter : document.book().chapters()) {
                    if (chapter.title() == null || chapter.title().isBlank()) {
                        throw new ApiException("IMPORT_INVALID", "Each chapter needs a title.", HttpStatus.BAD_REQUEST);
                    }
                    validateNotes(chapter.notes(), "chapter");
                    if (chapter.sections() != null) {
                        for (BookBackupDocument.SectionPayload section : chapter.sections()) {
                            if (section.title() == null || section.title().isBlank()) {
                                throw new ApiException("IMPORT_INVALID", "Each section needs a title.", HttpStatus.BAD_REQUEST);
                            }
                            validateNotes(section.notes(), "section");
                        }
                    }
                }
            }
            return document;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("IMPORT_INVALID", "Could not parse backup JSON: " + safeMessage(ex),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateNotes(List<BookBackupDocument.NotePayload> notes, String scope) {
        if (notes == null) {
            return;
        }
        for (BookBackupDocument.NotePayload note : notes) {
            if (note.attachType() != null) {
                try {
                    NoteAttachType.valueOf(note.attachType());
                } catch (Exception ex) {
                    throw new ApiException("IMPORT_INVALID",
                            "Invalid note attachType in " + scope + " notes.", HttpStatus.BAD_REQUEST);
                }
            }
            if (note.status() != null) {
                try {
                    NoteStatus.valueOf(note.status());
                } catch (Exception ex) {
                    throw new ApiException("IMPORT_INVALID",
                            "Invalid note status in " + scope + " notes.", HttpStatus.BAD_REQUEST);
                }
            }
        }
    }

    private void validateCover(BookBackupDocument.BookPayload book) {
        String b64 = book.coverBase64();
        String mime = book.coverMime();
        if ((b64 == null || b64.isBlank()) && (mime == null || mime.isBlank())) {
            return;
        }
        if (b64 == null || b64.isBlank() || mime == null || mime.isBlank()) {
            throw new ApiException("IMPORT_INVALID",
                    "Cover requires both coverMime and coverBase64.", HttpStatus.BAD_REQUEST);
        }
        if (!CoverStorageService.isAllowedMime(mime)) {
            throw new ApiException("IMPORT_INVALID",
                    "Cover must be JPEG, PNG, WebP, or GIF.", HttpStatus.BAD_REQUEST);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            if (bytes.length == 0 || bytes.length > CoverStorageService.MAX_BYTES) {
                throw new ApiException("IMPORT_INVALID",
                        "Cover payload is empty or exceeds 5MB.", HttpStatus.BAD_REQUEST);
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ApiException("IMPORT_INVALID", "coverBase64 is not valid base64.", HttpStatus.BAD_REQUEST);
        }
    }

    private void restoreCover(Book book, BookBackupDocument.BookPayload payload) {
        if (payload.coverBase64() == null || payload.coverBase64().isBlank()) {
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(payload.coverBase64());
        CoverStorageService.StoredCover stored = coverStorageService.storeBytes(
                book.getId(), payload.coverMime(), bytes);
        book.setCoverStorageKey(stored.storageKey());
        book.setCoverMime(stored.mimeType());
        bookRepository.save(book);
    }

    private BookBackupDocument.BookPayload buildBookPayload(Book book) {
        List<BookBackupDocument.NotePayload> bookNotes = noteRepository
                .findByBookIdAndAttachTypeOrderByPositionAsc(book.getId(), NoteAttachType.BOOK)
                .stream().map(this::toNotePayload).toList();

        List<Chapter> chapters = chapterRepository.findByBookIdOrderByPositionAsc(book.getId());
        List<BookBackupDocument.ChapterPayload> chapterPayloads = new ArrayList<>();
        for (Chapter chapter : chapters) {
            List<BookBackupDocument.NotePayload> chapterNotes = noteRepository
                    .findByChapterIdAndAttachTypeOrderByPositionAsc(chapter.getId(), NoteAttachType.CHAPTER)
                    .stream().map(this::toNotePayload).toList();
            List<Section> sections = sectionRepository.findByChapterIdOrderByPositionAsc(chapter.getId());
            List<BookBackupDocument.SectionPayload> sectionPayloads = new ArrayList<>();
            for (Section section : sections) {
                List<BookBackupDocument.NotePayload> sectionNotes = noteRepository
                        .findBySectionIdAndAttachTypeOrderByPositionAsc(section.getId(), NoteAttachType.SECTION)
                        .stream().map(this::toNotePayload).toList();
                sectionPayloads.add(new BookBackupDocument.SectionPayload(
                        section.getTitle(), section.getPosition(), sectionNotes));
            }
            chapterPayloads.add(new BookBackupDocument.ChapterPayload(
                    chapter.getTitle(), chapter.getPosition(), sectionPayloads, chapterNotes));
        }

        String coverMime = null;
        String coverBase64 = null;
        if (book.getCoverStorageKey() != null) {
            var bytes = coverStorageService.readBytes(book.getCoverStorageKey());
            if (bytes.isPresent()) {
                coverMime = book.getCoverMime() != null ? book.getCoverMime() : "image/jpeg";
                coverBase64 = Base64.getEncoder().encodeToString(bytes.get());
            }
        }

        return new BookBackupDocument.BookPayload(
                book.getTitle(),
                book.getDescription(),
                coverMime,
                coverBase64,
                chapterPayloads,
                bookNotes
        );
    }

    private BookBackupDocument.NotePayload toNotePayload(Note note) {
        List<String> tags = List.of();
        if (note.getTags() != null && !note.getTags().isBlank()) {
            try {
                tags = objectMapper.readValue(note.getTags(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception ignored) {
                tags = List.of();
            }
        }
        return new BookBackupDocument.NotePayload(
                note.getAttachType().name(),
                note.getTitle(),
                note.getBodyRich(),
                note.getBodyOriginal(),
                note.getBodyPlain(),
                note.getStatus().name(),
                note.getPageStart(),
                note.getPageEnd(),
                tags,
                note.getPosition()
        );
    }

    private void createNote(User user, Book book, NoteAttachType attachType, Chapter chapter, Section section,
                            BookBackupDocument.NotePayload payload, int fallbackPosition) {
        Note note = new Note();
        note.setBook(book);
        note.setAttachType(attachType);
        note.setChapter(chapter);
        note.setSection(section);
        note.setTitle(blankToNull(payload.title()));
        note.setBodyRich(payload.bodyRich());
        note.setBodyOriginal(payload.bodyOriginal());
        note.setBodyPlain(payload.bodyPlain() != null ? payload.bodyPlain() : toPlain(payload.bodyRich()));
        note.setStatus(payload.status() == null ? NoteStatus.SAVED : NoteStatus.valueOf(payload.status()));
        note.setPageStart(payload.pageStart());
        note.setPageEnd(payload.pageEnd());
        try {
            note.setTags(objectMapper.writeValueAsString(payload.tags() == null ? List.of() : payload.tags()));
        } catch (Exception e) {
            note.setTags("[]");
        }
        note.setPosition(payload.position() >= 0 ? payload.position() : fallbackPosition);
        note.setCreatedBy(user);
        note.setContentVersion(1);
        if (note.getStatus() == NoteStatus.SAVED && note.getBodyOriginal() == null && note.getBodyRich() != null) {
            note.setBodyOriginal(note.getBodyRich());
        }
        noteRepository.save(note);
    }

    private void appendNoteMarkdown(StringBuilder md, BookBackupDocument.NotePayload note, int headingLevel) {
        String hashes = "#".repeat(Math.min(6, headingLevel));
        String title = note.title() == null || note.title().isBlank() ? "Untitled note" : note.title();
        md.append(hashes).append(" ").append(escapeMd(title)).append("\n\n");
        if (note.pageStart() != null || note.pageEnd() != null) {
            md.append("_pp. ")
                    .append(note.pageStart() != null ? note.pageStart() : "?")
                    .append(note.pageEnd() != null && !note.pageEnd().equals(note.pageStart())
                            ? "–" + note.pageEnd() : "")
                    .append("_\n\n");
        }
        if (note.tags() != null && !note.tags().isEmpty()) {
            md.append("Tags: ").append(String.join(", ", note.tags())).append("\n\n");
        }
        String body = note.bodyPlain() != null ? note.bodyPlain() : toPlain(note.bodyRich());
        if (body != null && !body.isBlank()) {
            md.append(body.trim()).append("\n\n");
        }
    }

    private static String requireTitle(String title, String label) {
        if (title == null || title.isBlank()) {
            throw new ApiException("IMPORT_INVALID", label + " is required.", HttpStatus.BAD_REQUEST);
        }
        return title.trim();
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String toPlain(String bodyRich) {
        if (bodyRich == null) return null;
        String plain = TAG_STRIP.matcher(bodyRich).replaceAll(" ");
        plain = WHITESPACE.matcher(plain).replaceAll(" ").trim();
        return plain.isEmpty() ? null : plain;
    }

    private static String escapeMd(String value) {
        return value == null ? "" : value.replace("\n", " ").trim();
    }

    private static String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) return ex.getClass().getSimpleName();
        return msg.length() > 160 ? msg.substring(0, 160) + "…" : msg;
    }
}
