package com.rebeautifier.web;

import com.rebeautifier.service.AiSettingsService;
import com.rebeautifier.web.dto.AiPullResponse;
import com.rebeautifier.web.dto.AiSettingsResponse;
import com.rebeautifier.web.dto.UpdateAiSettingsRequest;
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

    /**
     * Persist AI settings only. Model pull is never done in this request — a long
     * Ollama download would make Save look broken (browser/proxy timeouts). Clients
     * that want pull-on-save should call {@code POST /pull-model} after a successful save.
     */
    @PutMapping
    public AiSettingsResponse save(@Valid @RequestBody UpdateAiSettingsRequest request) {
        AiSettingsResponse saved = aiSettingsService.saveForCurrentUser(request);
        if (Boolean.TRUE.equals(request.pullModelOnSave())
                && Boolean.TRUE.equals(request.enabled())
                && request.provider() != null
                && "OLLAMA".equals(request.provider().name())) {
            return new AiSettingsResponse(
                    saved.enabled(),
                    saved.provider(),
                    saved.model(),
                    saved.baseUrl(),
                    saved.apiKeyConfigured(),
                    saved.apiKeyHint(),
                    "AI settings saved. Start the model download with Pull model now (Save no longer waits on pull).",
                    saved.updatedAt()
            );
        }
        return saved;
    }

    @PostMapping("/pull-model")
    public AiPullResponse pullModel() {
        return aiSettingsService.pullModelForCurrentUser();
    }
}
