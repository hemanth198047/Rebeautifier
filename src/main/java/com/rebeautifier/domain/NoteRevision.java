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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "note_revisions")
public class NoteRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefineMode mode;

    @Column(name = "prompt_meta", columnDefinition = "TEXT")
    private String promptMeta;

    @Column(name = "input_content_version", nullable = false)
    private long inputContentVersion;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevisionStatus status = RevisionStatus.PENDING;

    @Column(name = "result_rich", columnDefinition = "TEXT")
    private String resultRich;

    @Column(name = "result_plain", columnDefinition = "TEXT")
    private String resultPlain;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Note getNote() {
        return note;
    }

    public void setNote(Note note) {
        this.note = note;
    }

    public RefineMode getMode() {
        return mode;
    }

    public void setMode(RefineMode mode) {
        this.mode = mode;
    }

    public String getPromptMeta() {
        return promptMeta;
    }

    public void setPromptMeta(String promptMeta) {
        this.promptMeta = promptMeta;
    }

    public long getInputContentVersion() {
        return inputContentVersion;
    }

    public void setInputContentVersion(long inputContentVersion) {
        this.inputContentVersion = inputContentVersion;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String inputHash) {
        this.inputHash = inputHash;
    }

    public RevisionStatus getStatus() {
        return status;
    }

    public void setStatus(RevisionStatus status) {
        this.status = status;
    }

    public String getResultRich() {
        return resultRich;
    }

    public void setResultRich(String resultRich) {
        this.resultRich = resultRich;
    }

    public String getResultPlain() {
        return resultPlain;
    }

    public void setResultPlain(String resultPlain) {
        this.resultPlain = resultPlain;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }
}
