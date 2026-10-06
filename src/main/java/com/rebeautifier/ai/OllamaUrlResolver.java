package com.rebeautifier.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks a reachable Ollama base URL from common Compose / Docker Desktop / localhost candidates.
 * Successful lookups are cached briefly so refine/status calls skip repeated multi-host probes.
 */
public final class OllamaUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(OllamaUrlResolver.class);
    private static final long CACHE_TTL_MS = 90_000L;
    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private OllamaUrlResolver() {
    }

    public static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:11434";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static List<String> candidates(String preferred) {
        Set<String> urls = new LinkedHashSet<>();
        if (preferred != null && !preferred.isBlank()) {
            urls.add(trimTrailingSlash(preferred));
        }
        urls.add("http://ollama:11434");
        urls.add("http://host.docker.internal:11434");
        urls.add("http://127.0.0.1:11434");
        urls.add("http://localhost:11434");
        return new ArrayList<>(urls);
    }

    public static String findReachable(HttpClient httpClient, String preferred) {
        String cacheKey = cacheKey(preferred);
        CacheEntry cached = CACHE.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < CACHE_TTL_MS) {
            return cached.url;
        }

        for (String candidate : candidates(preferred)) {
            if (isReachable(httpClient, candidate, Duration.ofMillis(700))) {
                if (preferred != null && !preferred.isBlank()
                        && !trimTrailingSlash(preferred).equals(candidate)) {
                    log.info("Ollama preferred URL {} unreachable; using {}", preferred, candidate);
                }
                CACHE.put(cacheKey, new CacheEntry(candidate, now));
                return candidate;
            }
        }
        String fallback = trimTrailingSlash(preferred);
        CACHE.remove(cacheKey);
        return fallback;
    }

    public static void invalidate(String preferred) {
        CACHE.remove(cacheKey(preferred));
    }

    /** Test helper — clears all cached URLs. */
    public static void clearCache() {
        CACHE.clear();
    }

    public static boolean isReachable(HttpClient httpClient, String baseUrl) {
        return isReachable(httpClient, baseUrl, Duration.ofMillis(700));
    }

    public static boolean isReachable(HttpClient httpClient, String baseUrl, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/tags"))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            log.debug("Ollama candidate {} not reachable: {}", baseUrl, ex.toString());
            return false;
        }
    }

    public static String unresolvedHint(String attemptedUrl) {
        return "Cannot resolve or reach Ollama at " + attemptedUrl
                + ". In Docker Compose, Ollama should be the `ollama` service (http://ollama:11434). "
                + "On Docker Desktop you can also try http://host.docker.internal:11434. "
                + "Restart with `./start.sh` (or `start.bat`) so the ollama container is running, "
                + "then set Base URL in Settings → AI if needed and pull again.";
    }

    public static boolean looksLikeDnsFailure(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String name = root.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String msg = root.getMessage() == null ? "" : root.getMessage().toLowerCase(Locale.ROOT);
        return name.contains("unresolved") || name.contains("unknownhost")
                || msg.contains("unresolved") || msg.contains("unknown host")
                || msg.contains("nodename nor servname");
    }

    private static String cacheKey(String preferred) {
        return preferred == null || preferred.isBlank() ? "" : trimTrailingSlash(preferred);
    }

    private record CacheEntry(String url, long atMs) {
    }
}
