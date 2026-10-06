package com.rebeautifier.config;

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
import com.rebeautifier.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final boolean seedDemoData;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BookMemberRepository bookMemberRepository;
    private final ChapterRepository chapterRepository;
    private final SectionRepository sectionRepository;
    private final NoteRepository noteRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(@Value("${rebeautifier.seed-demo-data:true}") boolean seedDemoData,
                          UserRepository userRepository,
                          BookRepository bookRepository,
                          BookMemberRepository bookMemberRepository,
                          ChapterRepository chapterRepository,
                          SectionRepository sectionRepository,
                          NoteRepository noteRepository,
                          PasswordEncoder passwordEncoder) {
        this.seedDemoData = seedDemoData;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.bookMemberRepository = bookMemberRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.noteRepository = noteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedDemoData) {
            return;
        }
        if (userRepository.existsByUsernameIgnoreCase("reader")) {
            if (noteRepository.count() == 0) {
                seedSampleNotesForExistingDemo();
            } else {
                log.info("Demo data already present; skipping seed");
            }
            return;
        }

        User reader = createUser("reader", "reader@rebeautifier.local", "Reader", "password123");
        User editor = createUser("editor", "editor@rebeautifier.local", "Editor Friend", "password123");

        Book garden = createBook(reader, "Morning Pages",
                "A quiet garden of half-formed thoughts waiting to be tended.");
        Chapter roots = createChapter(garden, "Roots", 0);
        Section woke = createSection(roots, "What woke with me", 0);
        Section unfinished = createSection(roots, "Unfinished sentences", 1);
        Chapter weather = createChapter(garden, "Weather of the mind", 1);
        Section clouds = createSection(weather, "Cloud notes", 0);

        Book atlas = createBook(reader, "Field Atlas",
                "Places, people, and patterns collected while walking.");
        Chapter trails = createChapter(atlas, "Local trails", 0);
        createSection(trails, "River bend", 0);
        createSection(trails, "Old oak", 1);

        BookMember membership = new BookMember();
        membership.setBook(garden);
        membership.setUser(editor);
        membership.setRole(BookRole.EDITOR);
        bookMemberRepository.save(membership);

        seedNotes(reader, garden, roots, woke, unfinished, weather, clouds);

        log.info("Seeded demo users: reader / password123 and editor / password123");
    }

    private void seedSampleNotesForExistingDemo() {
        User reader = userRepository.findByUsernameIgnoreCase("reader").orElse(null);
        if (reader == null) {
            return;
        }
        Book garden = bookRepository.findAll().stream()
                .filter(b -> "Morning Pages".equals(b.getTitle()) && b.getOwner().getId().equals(reader.getId()))
                .findFirst()
                .orElse(null);
        if (garden == null) {
            return;
        }
        List<Chapter> chapters = chapterRepository.findByBookIdOrderByPositionAsc(garden.getId());
        if (chapters.isEmpty()) {
            return;
        }
        Chapter roots = chapters.get(0);
        Chapter weather = chapters.size() > 1 ? chapters.get(1) : roots;
        List<Section> rootSections = sectionRepository.findByChapterIdOrderByPositionAsc(roots.getId());
        List<Section> weatherSections = sectionRepository.findByChapterIdOrderByPositionAsc(weather.getId());
        Section woke = rootSections.isEmpty() ? null : rootSections.get(0);
        Section unfinished = rootSections.size() > 1 ? rootSections.get(1) : woke;
        Section clouds = weatherSections.isEmpty() ? woke : weatherSections.get(0);
        seedNotes(reader, garden, roots, woke, unfinished, weather, clouds);
        log.info("Seeded Phase 2 sample notes for existing demo library");
    }

    private void seedNotes(User author, Book garden, Chapter roots, Section woke, Section unfinished,
                           Chapter weather, Section clouds) {
        createNote(author, garden, NoteAttachType.BOOK, null, null,
                "Why this book exists",
                "<p>Morning Pages is a soft landing for thoughts that arrive before coffee.</p><p>Nothing here needs to be finished on the first pass.</p>",
                NoteStatus.SAVED, null, null, "[\"purpose\",\"ritual\"]", 0);

        if (roots != null) {
            createNote(author, garden, NoteAttachType.CHAPTER, roots, null,
                    "Roots — framing",
                    "<p>Begin with what is already growing: habits, worries, and quiet hopes.</p><ul><li>Notice without fixing</li><li>Keep the original wording</li></ul>",
                    NoteStatus.SAVED, 1, 3, "[\"framing\"]", 0);
        }

        if (woke != null) {
            createNote(author, garden, NoteAttachType.SECTION, roots, woke,
                    "First light",
                    "<p>The window was pale blue. I wrote three sentences and crossed one out — then put it back.</p><blockquote>Keep the crossed-out line; it still belongs.</blockquote>",
                    NoteStatus.SAVED, 12, 12, "[\"morning\",\"sensory\"]", 0);
        }

        if (unfinished != null) {
            createNote(author, garden, NoteAttachType.SECTION, roots, unfinished,
                    "Draft thought (unsaved shape)",
                    "<p>There was something about the train whistle and how it made the kitchen feel larger…</p>",
                    NoteStatus.DRAFT, null, null, "[\"draft\"]", 0);
        }

        if (clouds != null) {
            createNote(author, garden, NoteAttachType.SECTION, weather, clouds,
                    "Passing weather",
                    "<p><strong>Today:</strong> a low pressure of unfinished emails.</p><p>Name the weather; do not become it.</p>",
                    NoteStatus.SAVED, 40, 42, "[\"metaphor\",\"weather\"]", 0);
        }
    }

    private void createNote(User author, Book book, NoteAttachType attachType, Chapter chapter, Section section,
                            String title, String bodyHtml, NoteStatus status, Integer pageStart, Integer pageEnd,
                            String tagsJson, int position) {
        Note note = new Note();
        note.setBook(book);
        note.setAttachType(attachType);
        note.setChapter(chapter);
        note.setSection(section);
        note.setTitle(title);
        note.setBodyRich(bodyHtml);
        note.setBodyPlain(bodyHtml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
        if (status == NoteStatus.SAVED) {
            note.setBodyOriginal(bodyHtml);
        }
        note.setStatus(status);
        note.setPageStart(pageStart);
        note.setPageEnd(pageEnd);
        note.setTags(tagsJson);
        note.setPosition(position);
        note.setCreatedBy(author);
        note.setContentVersion(1);
        noteRepository.save(note);
    }

    private User createUser(String username, String email, String displayName, String password) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    private Book createBook(User owner, String title, String description) {
        Book book = new Book();
        book.setOwner(owner);
        book.setTitle(title);
        book.setDescription(description);
        bookRepository.save(book);

        BookMember ownerMembership = new BookMember();
        ownerMembership.setBook(book);
        ownerMembership.setUser(owner);
        ownerMembership.setRole(BookRole.OWNER);
        bookMemberRepository.save(ownerMembership);
        return book;
    }

    private Chapter createChapter(Book book, String title, int position) {
        Chapter chapter = new Chapter();
        chapter.setBook(book);
        chapter.setTitle(title);
        chapter.setPosition(position);
        return chapterRepository.save(chapter);
    }

    private Section createSection(Chapter chapter, String title, int position) {
        Section section = new Section();
        section.setChapter(chapter);
        section.setTitle(title);
        section.setPosition(position);
        return sectionRepository.save(section);
    }
}
