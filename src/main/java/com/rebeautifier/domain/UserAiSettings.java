package com.rebeautifier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_ai_settings")
public class UserAiSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiProvider provider = AiProvider.OLLAMA;

    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName = "llama3.2:1b";

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
