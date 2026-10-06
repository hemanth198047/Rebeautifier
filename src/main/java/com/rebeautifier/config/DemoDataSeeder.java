package com.rebeautifier.config;

import com.rebeautifier.domain.Book;
import com.rebeautifier.domain.BookMember;
import com.rebeautifier.domain.BookRole;
import com.rebeautifier.domain.Chapter;
import com.rebeautifier.domain.Section;
import com.rebeautifier.domain.User;
import com.rebeautifier.repository.BookMemberRepository;
import com.rebeautifier.repository.BookRepository;
import com.rebeautifier.repository.ChapterRepository;
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

@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final boolean seedDemoData;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BookMemberRepository bookMemberRepository;
    private final ChapterRepository chapterRepository;
    private final SectionRepository sectionRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(@Value("${rebeautifier.seed-demo-data:true}") boolean seedDemoData,
                          UserRepository userRepository,
                          BookRepository bookRepository,
                          BookMemberRepository bookMemberRepository,
                          ChapterRepository chapterRepository,
                          SectionRepository sectionRepository,
                          PasswordEncoder passwordEncoder) {
        this.seedDemoData = seedDemoData;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.bookMemberRepository = bookMemberRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedDemoData) {
            return;
        }
        if (userRepository.existsByUsernameIgnoreCase("reader")) {
            log.info("Demo data already present; skipping seed");
            return;
        }

        User reader = createUser("reader", "reader@rebeautifier.local", "Reader", "password123");
        User editor = createUser("editor", "editor@rebeautifier.local", "Editor Friend", "password123");

        Book garden = createBook(reader, "Morning Pages",
                "A quiet garden of half-formed thoughts waiting to be tended.");
        Chapter roots = createChapter(garden, "Roots", 0);
        createSection(roots, "What woke with me", 0);
        createSection(roots, "Unfinished sentences", 1);
        Chapter weather = createChapter(garden, "Weather of the mind", 1);
        createSection(weather, "Cloud notes", 0);

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

        log.info("Seeded demo users: reader / password123 and editor / password123");
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
