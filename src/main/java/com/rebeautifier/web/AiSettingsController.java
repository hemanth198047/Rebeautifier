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

    @PutMapping
    public AiSettingsResponse save(@Valid @RequestBody UpdateAiSettingsRequest request) {
        return aiSettingsService.saveForCurrentUser(request);
    }

    @PostMapping("/pull-model")
    public AiPullResponse pullModel() {
        return aiSettingsService.pullModelForCurrentUser();
    }
}
