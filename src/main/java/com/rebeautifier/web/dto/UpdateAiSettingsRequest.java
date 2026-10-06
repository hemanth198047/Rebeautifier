package com.rebeautifier.web.dto;

import com.rebeautifier.domain.AiProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAiSettingsRequest(
        @NotNull Boolean enabled,
        @NotNull AiProvider provider,
        @NotBlank @Size(max = 200) String model,
        @Size(max = 500) String baseUrl,
        @Size(max = 4000) String apiKey,
        Boolean clearApiKey,
        Boolean pullModelOnSave
) {
}
