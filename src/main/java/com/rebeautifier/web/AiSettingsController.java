package com.rebeautifier.web;

import com.rebeautifier.service.AiSettingsService;
import com.rebeautifier.web.dto.AiPullResponse;
import com.rebeautifier.web.dto.AiSettingsResponse;
import com.rebeautifier.web.dto.UpdateAiSettingsRequest;
import com.rebeautifier.web.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/ai")
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    public AiSettingsController(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    @GetMapping
    public AiSettingsResponse get() {
        return aiSettingsService.getForCurrentUser();
    }

    @PutMapping
    public AiSettingsResponse save(@Valid @RequestBody UpdateAiSettingsRequest request) {
        // Commit settings first (separate transaction boundary via service method return).
        AiSettingsResponse saved = aiSettingsService.saveForCurrentUser(request);

        boolean wantPull = Boolean.TRUE.equals(request.pullModelOnSave())
                && Boolean.TRUE.equals(request.enabled())
                && request.provider() != null
                && "OLLAMA".equals(request.provider().name());

        if (!wantPull) {
            return saved;
        }

        try {
            AiPullResponse pull = aiSettingsService.pullModelForCurrentUser();
            String msg = pull.message()
                    + (pull.nextStep() != null ? " " + pull.nextStep() : "");
            return new AiSettingsResponse(
                    saved.enabled(),
                    saved.provider(),
                    saved.model(),
                    saved.baseUrl(),
                    saved.apiKeyConfigured(),
                    saved.apiKeyHint(),
                    msg,
                    saved.updatedAt()
            );
        } catch (ApiException ex) {
            // Keep the saved enablement; surface pull failure as guidance, not a rollback.
            return new AiSettingsResponse(
                    saved.enabled(),
                    saved.provider(),
                    saved.model(),
                    saved.baseUrl(),
                    saved.apiKeyConfigured(),
                    saved.apiKeyHint(),
                    "AI settings saved, but model pull failed: " + ex.getMessage(),
                    saved.updatedAt()
            );
        }
    }

    @PostMapping("/pull-model")
    public AiPullResponse pullModel() {
        return aiSettingsService.pullModelForCurrentUser();
    }
}
