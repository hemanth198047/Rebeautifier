package com.rebeautifier.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final Duration timeout;
    private final boolean mockEnabled;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(@Value("${rebeautifier.ollama.timeout-seconds:90}") long timeoutSeconds,
                                  @Value("${rebeautifier.ollama.mock-enabled:false}") boolean mockEnabled,
                                  ObjectMapper objectMapper) {
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.mockEnabled = mockEnabled;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public OllamaClient.AiStatusProbe probe(AiRuntimeConfig config) {
        if (mockEnabled) {
            return new OllamaClient.AiStatusProbe(true, true, "Mock OpenAI-compatible provider enabled", 1L,
                    List.of(config.model()));
        }
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            return OllamaClient.AiStatusProbe.unavailable("API key is required for this provider", 0L, List.of());
        }
        long started = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/models"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .GET();
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - started;
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return OllamaClient.AiStatusProbe.unavailable("API key was rejected by the provider", latency, List.of());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // Some OpenAI-compatible hosts don't expose /models — treat 404 as reachable with configured model.
                if (response.statusCode() == 404) {
                    return new OllamaClient.AiStatusProbe(true, true,
                            "Provider reachable (models list unavailable); using configured model",
                            latency, List.of(config.model()));
                }
                return OllamaClient.AiStatusProbe.unavailable(
                        "Provider returned HTTP " + response.statusCode(), latency, List.of());
            }
            return new OllamaClient.AiStatusProbe(true, true, "Provider reachable; API key accepted", latency,
                    List.of(config.model()));
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - started;
            log.debug("OpenAI-compatible probe failed: {}", ex.toString());
            return OllamaClient.AiStatusProbe.unavailable("Provider unreachable: " + shortMessage(ex), latency, List.of());
        }
    }

    public String generate(AiRuntimeConfig config, String systemPrompt, String userPrompt) throws OllamaClient.OllamaException {
        if (mockEnabled) {
            return "<p><strong>Mock refine</strong></p><p>" + escape(userPrompt) + "</p>";
        }
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new OllamaClient.OllamaException("API key is required");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.model());
            body.put("temperature", 0.2);
            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OllamaClient.OllamaException("Provider generate failed with HTTP " + response.statusCode()
                        + (response.body() != null && !response.body().isBlank()
                        ? ": " + truncate(response.body(), 180) : ""));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("choices").path(0).path("message").path("content").asText(null);
            if (text == null || text.isBlank()) {
                throw new OllamaClient.OllamaException("Provider returned an empty response");
            }
            return text.trim();
        } catch (OllamaClient.OllamaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OllamaClient.OllamaException("Provider generate failed: " + shortMessage(ex), ex);
        }
    }

    private static String shortMessage(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return msg.length() > 180 ? msg.substring(0, 180) + "…" : msg;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String escape(String input) {
        String snippet = input == null ? "" : input;
        if (snippet.length() > 280) {
            snippet = snippet.substring(0, 280) + "…";
        }
        return snippet.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", " ");
    }
}
