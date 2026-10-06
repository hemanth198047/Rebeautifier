package com.rebeautifier.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final boolean mockEnabled;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(@Value("${rebeautifier.ollama.base-url:http://localhost:11434}") String baseUrl,
                        @Value("${rebeautifier.ollama.model:llama3.2:1b}") String model,
                        @Value("${rebeautifier.ollama.timeout-seconds:90}") long timeoutSeconds,
                        @Value("${rebeautifier.ollama.mock-enabled:false}") boolean mockEnabled,
                        ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.mockEnabled = mockEnabled;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String configuredModel() {
        return model;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public AiStatusProbe probe() {
        if (mockEnabled) {
            return new AiStatusProbe(true, true, "Mock Ollama enabled for tests", 1L, List.of(model));
        }
        long started = System.currentTimeMillis();
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
                    if (name != null && !name.isBlank()) {
                        models.add(name);
                    }
                }
            }
            boolean hasModel = models.stream().anyMatch(n -> n.equals(model) || n.startsWith(model + ":") || model.startsWith(n.split(":")[0]));
            // Also match exact tag contained in list (e.g. llama3.2:1b)
            if (!hasModel) {
                hasModel = models.stream().anyMatch(n -> n.equals(model) || n.startsWith(model));
            }
            String message = hasModel
                    ? "Ollama reachable; model available"
                    : "Ollama reachable but model '" + model + "' not pulled yet";
            return new AiStatusProbe(true, hasModel, message, latency, models);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - started;
            log.debug("Ollama probe failed: {}", ex.toString());
            return AiStatusProbe.unavailable("Ollama unreachable: " + shortMessage(ex), latency, List.of());
        }
    }

    public String generate(String systemPrompt, String userPrompt) throws OllamaException {
        if (mockEnabled) {
            return "<p><strong>Mock refine</strong></p><p>" + escapeForMock(userPrompt) + "</p>";
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
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
            throw new OllamaException("Ollama generate failed: " + shortMessage(ex), ex);
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:11434";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String shortMessage(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return msg.length() > 180 ? msg.substring(0, 180) + "…" : msg;
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

    public static class OllamaException extends Exception {
        public OllamaException(String message) {
            super(message);
        }

        public OllamaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
