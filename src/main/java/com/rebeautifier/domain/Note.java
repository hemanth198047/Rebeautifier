package com.rebeautifier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "attach_type", nullable = false, length = 20)
    private NoteAttachType attachType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(length = 200)
    private String title;

    @Column(name = "body_rich", columnDefinition = "TEXT")
    private String bodyRich;

    @Column(name = "body_original", columnDefinition = "TEXT")
    private String bodyOriginal;

    @Column(name = "body_plain", columnDefinition = "TEXT")
    private String bodyPlain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteStatus status = NoteStatus.DRAFT;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    /** JSON array of tag strings, e.g. ["morning","idea"]. */
    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "content_version", nullable = false)
    private long contentVersion = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public NoteAttachType getAttachType() {
        return attachType;
    }

    public void setAttachType(NoteAttachType attachType) {
        this.attachType = attachType;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public void setChapter(Chapter chapter) {
        this.chapter = chapter;
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBodyRich() {
        return bodyRich;
    }

    public void setBodyRich(String bodyRich) {
        this.bodyRich = bodyRich;
    }

    public String getBodyOriginal() {
        return bodyOriginal;
    }

    public void setBodyOriginal(String bodyOriginal) {
        this.bodyOriginal = bodyOriginal;
    }

    public String getBodyPlain() {
        return bodyPlain;
    }

    public void setBodyPlain(String bodyPlain) {
        this.bodyPlain = bodyPlain;
    }

    public NoteStatus getStatus() {
        return status;
    }

    public void setStatus(NoteStatus status) {
        this.status = status;
    }

    public Integer getPageStart() {
        return pageStart;
    }

    public void setPageStart(Integer pageStart) {
        this.pageStart = pageStart;
    }

    public Integer getPageEnd() {
        return pageEnd;
    }

    public void setPageEnd(Integer pageEnd) {
        this.pageEnd = pageEnd;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public long getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(long contentVersion) {
        this.contentVersion = contentVersion;
    }

    public void bumpContentVersion() {
        this.contentVersion += 1;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
