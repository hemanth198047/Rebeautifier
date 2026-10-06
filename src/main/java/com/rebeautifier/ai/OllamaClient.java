package com.rebeautifier.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rebeautifier.domain.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final String defaultBaseUrl;
    private final String defaultModel;
    private final Duration timeout;
    private final boolean mockEnabled;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(@Value("${rebeautifier.ollama.base-url:http://localhost:11434}") String baseUrl,
                        @Value("${rebeautifier.ollama.model:llama3.2:1b}") String model,
                        @Value("${rebeautifier.ollama.timeout-seconds:90}") long timeoutSeconds,
                        @Value("${rebeautifier.ollama.mock-enabled:false}") boolean mockEnabled,
                        ObjectMapper objectMapper) {
        this.defaultBaseUrl = trimTrailingSlash(baseUrl);
        this.defaultModel = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.mockEnabled = mockEnabled;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean mockEnabled() {
        return mockEnabled;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    /** @deprecated prefer defaultModel() — kept for existing callers during migration */
    public String configuredModel() {
        return defaultModel;
    }

    public String baseUrl() {
        return defaultBaseUrl;
    }

    public AiStatusProbe probe() {
        return probe(defaultConfig());
    }

    public AiStatusProbe probe(AiRuntimeConfig config) {
        if (mockEnabled) {
            return new AiStatusProbe(true, true, "Mock Ollama enabled for tests", 1L, List.of(config.model()));
        }
        long started = System.currentTimeMillis();
        String baseUrl = OllamaUrlResolver.findReachable(httpClient, config.baseUrl());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - started;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return AiStatusProbe.unavailable("Ollama returned HTTP " + response.statusCode(), latency, List.of());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<String> models = new ArrayList<>();
            JsonNode modelsNode = root.path("models");
            if (modelsNode.isArray()) {
                for (JsonNode m : modelsNode) {
                    String name = m.path("name").asText(null);
                    if (name == null || name.isBlank()) {
                        name = m.path("model").asText(null);
                    }
                    if (name != null && !name.isBlank() && !models.contains(name)) {
                        models.add(name);
                    }
                }
            }
            boolean hasModel = models.stream().anyMatch(n -> modelMatches(config.model(), n));
            String message = hasModel
                    ? "Ollama reachable at " + baseUrl + "; model '" + config.model() + "' is available"
                    : models.isEmpty()
                    ? "Ollama reachable at " + baseUrl + " but no models are installed yet — pull '"
                            + config.model() + "'"
                    : "Ollama reachable at " + baseUrl + " but model '" + config.model()
                            + "' is not installed. Installed: " + String.join(", ", models);
            return new AiStatusProbe(true, hasModel, message, latency, models);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - started;
            log.debug("Ollama probe failed: {}", ex.toString());
            if (OllamaUrlResolver.looksLikeDnsFailure(ex)) {
                return AiStatusProbe.unavailable(OllamaUrlResolver.unresolvedHint(baseUrl), latency, List.of());
            }
            return AiStatusProbe.unavailable(friendlyUnreachable(ex), latency, List.of());
        }
    }

    public String generate(String systemPrompt, String userPrompt) throws OllamaException {
        return generate(defaultConfig(), systemPrompt, userPrompt);
    }

    public String generate(AiRuntimeConfig config, String systemPrompt, String userPrompt) throws OllamaException {
        if (mockEnabled) {
            return "<p><strong>Mock refine</strong></p><p>" + escapeForMock(userPrompt) + "</p>";
        }
        String baseUrl = OllamaUrlResolver.findReachable(httpClient, config.baseUrl());
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.model());
            body.put("stream", false);
            body.put("system", systemPrompt);
            body.put("prompt", userPrompt);
            ObjectNode options = body.putObject("options");
            options.put("temperature", 0.2);
            options.put("num_predict", 2048);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OllamaException("Ollama generate failed with HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("response").asText(null);
            if (text == null || text.isBlank()) {
                throw new OllamaException("Ollama returned an empty response");
            }
            return text.trim();
        } catch (OllamaException ex) {
            throw ex;
        } catch (Exception ex) {
            if (OllamaUrlResolver.looksLikeDnsFailure(ex)) {
                throw new OllamaException(OllamaUrlResolver.unresolvedHint(baseUrl), ex);
            }
            throw new OllamaException("Ollama generate failed: " + shortMessage(ex), ex);
        }
    }

    public PullResult pullModel(AiRuntimeConfig config) throws OllamaException {
        if (mockEnabled) {
            return new PullResult(true, "Mock pull succeeded for " + config.model());
        }
        String baseUrl = OllamaUrlResolver.findReachable(httpClient, config.baseUrl());
        if (!OllamaUrlResolver.isReachable(httpClient, baseUrl)) {
            throw new OllamaException(OllamaUrlResolver.unresolvedHint(config.baseUrl()));
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", config.model());
            body.put("stream", false);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/pull"))
                    .timeout(Duration.ofMinutes(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String responseBody = response.body() == null ? "" : response.body();
                String registryErr = extractRegistryError(responseBody);
                if (registryErr != null) {
                    throw new OllamaException(classifyPullFailure(config.model(), baseUrl, registryErr));
                }
                throw new OllamaException("Ollama pull failed with HTTP " + response.statusCode()
                        + (responseBody.isBlank() ? "" : ": " + truncate(responseBody, 200)));
            }
            // Ollama may return 200 with an error payload when registry DNS/network fails
            // or when the model name is not in the library.
            if (response.body() != null && response.body().toLowerCase().contains("error")) {
                String registryErr = extractRegistryError(response.body());
                if (registryErr != null) {
                    throw new OllamaException(classifyPullFailure(config.model(), baseUrl, registryErr));
                }
            }
            // Confirm the model actually appears in /api/tags — never report success on an empty install.
            AiStatusProbe after = probe(config);
            if (!after.modelAvailable()) {
                throw new OllamaException(
                        "Pull finished contacting Ollama at " + baseUrl
                                + ", but model '" + config.model() + "' is still not installed. "
                                + (after.models().isEmpty()
                                ? "No models are listed yet — the download may have failed silently, or a different Ollama instance was used."
                                : "Installed models: " + String.join(", ", after.models()) + ".")
                                + " Check that Settings → AI Base URL points at the same Ollama that received the pull.");
            }
            return new PullResult(true, "Pulled model " + config.model() + " via " + baseUrl);
        } catch (OllamaException ex) {
            throw ex;
        } catch (Exception ex) {
            if (OllamaUrlResolver.looksLikeDnsFailure(ex)) {
                throw new OllamaException(OllamaUrlResolver.unresolvedHint(baseUrl), ex);
            }
            throw new OllamaException("Ollama pull failed: " + shortMessage(ex), ex);
        }
    }

    public AiRuntimeConfig defaultConfig() {
        return new AiRuntimeConfig(AiProvider.OLLAMA, defaultBaseUrl, defaultModel, null);
    }

    private static String trimTrailingSlash(String url) {
        return OllamaUrlResolver.trimTrailingSlash(url);
    }

    /** True when an installed tag satisfies the configured model name. */
    static boolean modelMatches(String configured, String installed) {
        if (configured == null || installed == null) {
            return false;
        }
        String want = configured.trim();
        String have = installed.trim();
        if (want.isEmpty() || have.isEmpty()) {
            return false;
        }
        if (have.equals(want) || have.startsWith(want + ":") || want.startsWith(have + ":")) {
            return true;
        }
        String wantBase = want.contains(":") ? want.substring(0, want.indexOf(':')) : want;
        String haveBase = have.contains(":") ? have.substring(0, have.indexOf(':')) : have;
        return wantBase.equals(haveBase);
    }

    private static String friendlyUnreachable(Exception ex) {
        String detail = shortMessage(ex);
        String lower = detail.toLowerCase();
        if (lower.contains("connect") || lower.contains("timed out") || lower.contains("unreachable")
                || lower.contains("unknown host") || lower.contains("connection refused")
                || lower.contains("unresolved")) {
            return "Ollama is not running or not reachable at the configured URL";
        }
        return "Ollama unreachable: " + detail;
    }

    private static String shortMessage(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = root.getClass().getSimpleName();
        }
        if ("ConnectException".equals(msg) || "Connection refused".equalsIgnoreCase(msg)) {
            return "connection refused";
        }
        return msg.length() > 180 ? msg.substring(0, 180) + "…" : msg;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private String extractRegistryError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String err = root.path("error").asText(null);
            if (err != null && !err.isBlank()) {
                return err;
            }
        } catch (Exception ignored) {
            // fall through
        }
        String lower = body.toLowerCase();
        if (lower.contains("registry.ollama.ai") || lower.contains("pull model manifest")
                || lower.contains("i/o timeout") || lower.contains("dial tcp")) {
            return truncate(body, 240);
        }
        return null;
    }

    private static String classifyPullFailure(String model, String baseUrl, String registryErr) {
        if (OllamaModelNames.looksLikeMissingLibraryModel(registryErr)
                || OllamaModelNames.looksLikeCloudApiModel(model)) {
            return OllamaModelNames.missingLibraryModelHint(model);
        }
        return registryConnectivityMessage(baseUrl, registryErr);
    }

    private static String registryConnectivityMessage(String baseUrl, String registryErr) {
        return "Ollama is reachable at " + baseUrl
                + " but could not download from the model registry (registry.ollama.ai). "
                + "Detail: " + truncate(registryErr, 200)
                + " Ensure the ollama container has outbound HTTPS, or pull on the host with "
                + "`docker compose exec ollama ollama pull <model>` / `./start.sh --pull-model`, then retry.";
    }

    private static String escapeForMock(String input) {
        String snippet = input == null ? "" : input;
        int noteIdx = snippet.toUpperCase().lastIndexOf("NOTE:");
        if (noteIdx >= 0) {
            snippet = snippet.substring(noteIdx + 5).trim();
        }
        if (snippet.length() > 280) {
            snippet = snippet.substring(0, 280) + "…";
        }
        return snippet.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", " ");
    }

    public record AiStatusProbe(
            boolean reachable,
            boolean modelAvailable,
            String message,
            long latencyMs,
            List<String> models
    ) {
        public static AiStatusProbe unavailable(String message, long latencyMs, List<String> models) {
            return new AiStatusProbe(false, false, message, latencyMs, models);
        }
    }

    public record PullResult(boolean success, String message) {
    }

    public static class OllamaException extends Exception {
        public OllamaException(String message) {
            super(message);
        }

        public OllamaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
