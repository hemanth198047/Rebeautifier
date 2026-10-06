package com.rebeautifier.service;

import com.rebeautifier.ai.AiRuntimeConfig;
import com.rebeautifier.ai.OllamaClient;
import com.rebeautifier.ai.OpenAiCompatibleClient;
import com.rebeautifier.domain.AiProvider;
import com.rebeautifier.domain.User;
import com.rebeautifier.domain.UserAiSettings;
import com.rebeautifier.repository.UserAiSettingsRepository;
import com.rebeautifier.web.dto.AiPullResponse;
import com.rebeautifier.web.dto.AiSettingsResponse;
import com.rebeautifier.web.dto.UpdateAiSettingsRequest;
import com.rebeautifier.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiSettingsService {

    private final UserAiSettingsRepository repository;
    private final AuthService authService;
    private final OllamaClient ollamaClient;
    private final OpenAiCompatibleClient openAiCompatibleClient;

    public AiSettingsService(UserAiSettingsRepository repository,
                             AuthService authService,
                             OllamaClient ollamaClient,
                             OpenAiCompatibleClient openAiCompatibleClient) {
        this.repository = repository;
        this.authService = authService;
        this.ollamaClient = ollamaClient;
        this.openAiCompatibleClient = openAiCompatibleClient;
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse getForCurrentUser() {
        User user = authService.requireCurrentUser();
        return toResponse(loadOrDefaults(user), null);
    }

    @Transactional
    public AiSettingsResponse saveForCurrentUser(UpdateAiSettingsRequest request) {
        User user = authService.requireCurrentUser();
        UserAiSettings settings = repository.findById(user.getId()).orElseGet(() -> {
            UserAiSettings created = new UserAiSettings();
            created.setUserId(user.getId());
            return created;
        });

        settings.setEnabled(Boolean.TRUE.equals(request.enabled()));
        settings.setProvider(request.provider());
        settings.setModelName(request.model().trim());

        String baseUrl = request.baseUrl() == null ? null : request.baseUrl().trim();
        if (baseUrl != null && baseUrl.isBlank()) {
            baseUrl = null;
        }
        settings.setBaseUrl(baseUrl);

        if (Boolean.TRUE.equals(request.clearApiKey())) {
            settings.setApiKey(null);
        } else if (request.apiKey() != null && !request.apiKey().isBlank()) {
            settings.setApiKey(request.apiKey().trim());
        }

        if (settings.getProvider() == AiProvider.OPENAI_COMPATIBLE
                && settings.isEnabled()
                && (settings.getApiKey() == null || settings.getApiKey().isBlank())
                && !ollamaClient.mockEnabled()) {
            throw ApiException.badRequest("API key is required when using an OpenAI-compatible provider");
        }

        repository.save(settings);

        String pullMessage = null;
        if (Boolean.TRUE.equals(request.pullModelOnSave())
                && settings.isEnabled()
                && settings.getProvider() == AiProvider.OLLAMA) {
            pullMessage = pullModel(resolveConfig(settings)).message();
        }

        return toResponse(settings, pullMessage);
    }

    @Transactional(readOnly = true)
    public AiPullResponse pullModelForCurrentUser() {
        User user = authService.requireCurrentUser();
        UserAiSettings settings = loadOrDefaults(user);
        if (!settings.isEnabled()) {
            throw new ApiException("AI_DISABLED", "Enable AI in Settings before pulling a model.", HttpStatus.BAD_REQUEST);
        }
        if (settings.getProvider() != AiProvider.OLLAMA) {
            throw ApiException.badRequest("Model pull is only available for the local Ollama provider");
        }
        return pullModel(resolveConfig(settings));
    }

    @Transactional(readOnly = true)
    public ResolvedAi resolveForCurrentUser() {
        User user = authService.requireCurrentUser();
        UserAiSettings settings = loadOrDefaults(user);
        return new ResolvedAi(settings.isEnabled(), settings.getProvider(), resolveConfig(settings), settings);
    }

    public OllamaClient.AiStatusProbe probe(ResolvedAi resolved) {
        if (resolved.provider() == AiProvider.OPENAI_COMPATIBLE) {
            return openAiCompatibleClient.probe(resolved.config());
        }
        return ollamaClient.probe(resolved.config());
    }

    public String generate(ResolvedAi resolved, String systemPrompt, String userPrompt) throws OllamaClient.OllamaException {
        if (resolved.provider() == AiProvider.OPENAI_COMPATIBLE) {
            return openAiCompatibleClient.generate(resolved.config(), systemPrompt, userPrompt);
        }
        return ollamaClient.generate(resolved.config(), systemPrompt, userPrompt);
    }

    private AiPullResponse pullModel(AiRuntimeConfig config) {
        try {
            OllamaClient.PullResult result = ollamaClient.pullModel(config);
            OllamaClient.AiStatusProbe probe = ollamaClient.probe(config);
            return new AiPullResponse(result.success(), result.message(), probe.modelAvailable());
        } catch (OllamaClient.OllamaException ex) {
            throw new ApiException("AI_PULL_FAILED", ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private UserAiSettings loadOrDefaults(User user) {
        return repository.findById(user.getId()).orElseGet(() -> {
            UserAiSettings defaults = new UserAiSettings();
            defaults.setUserId(user.getId());
            // Mock mode (tests) starts enabled so existing refine coverage keeps working.
            defaults.setEnabled(ollamaClient.mockEnabled());
            defaults.setProvider(AiProvider.OLLAMA);
            defaults.setModelName(ollamaClient.defaultModel());
            defaults.setBaseUrl(null);
            return defaults;
        });
    }

    public AiRuntimeConfig resolveConfig(UserAiSettings settings) {
        AiProvider provider = settings.getProvider() == null ? AiProvider.OLLAMA : settings.getProvider();
        String model = settings.getModelName() == null || settings.getModelName().isBlank()
                ? ollamaClient.defaultModel()
                : settings.getModelName().trim();
        String baseUrl = settings.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = provider == AiProvider.OPENAI_COMPATIBLE
                    ? OpenAiCompatibleClient.DEFAULT_BASE_URL
                    : ollamaClient.defaultBaseUrl();
        } else {
            baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }
        return new AiRuntimeConfig(provider, baseUrl, model, settings.getApiKey());
    }

    private AiSettingsResponse toResponse(UserAiSettings settings, String pullMessage) {
        AiRuntimeConfig config = resolveConfig(settings);
        String key = settings.getApiKey();
        boolean configured = key != null && !key.isBlank();
        String hint = null;
        if (configured) {
            hint = key.length() <= 4 ? "••••" : "••••" + key.substring(key.length() - 4);
        }
        return new AiSettingsResponse(
                settings.isEnabled(),
                settings.getProvider() == null ? AiProvider.OLLAMA : settings.getProvider(),
                config.model(),
                config.baseUrl(),
                configured,
                hint,
                pullMessage,
                settings.getUpdatedAt()
        );
    }

    public record ResolvedAi(
            boolean enabled,
            AiProvider provider,
            AiRuntimeConfig config,
            UserAiSettings settings
    ) {
    }
}
