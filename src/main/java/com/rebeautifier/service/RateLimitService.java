package com.rebeautifier.service;

import com.rebeautifier.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple sliding-window rate limiter for auth and AI mutation endpoints.
 */
@Service
public class RateLimitService {

    private final int authPerMinute;
    private final int aiPerMinute;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${rebeautifier.rate-limit.auth-per-minute:30}") int authPerMinute,
            @Value("${rebeautifier.rate-limit.ai-per-minute:20}") int aiPerMinute
    ) {
        this.authPerMinute = Math.max(1, authPerMinute);
        this.aiPerMinute = Math.max(1, aiPerMinute);
    }

    public void checkAuth(String clientKey) {
        check("auth:" + sanitize(clientKey), authPerMinute, Duration.ofMinutes(1),
                "Too many auth attempts. Wait a minute and try again.");
    }

    public void checkAi(String clientKey) {
        check("ai:" + sanitize(clientKey), aiPerMinute, Duration.ofMinutes(1),
                "Too many AI refine requests. Wait a minute and try again.");
    }

    private void check(String key, int max, Duration window, String message) {
        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();
        Deque<Long> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) {
                q.removeFirst();
            }
            if (q.size() >= max) {
                long oldest = q.peekFirst() == null ? now : q.peekFirst();
                int retryAfter = (int) Math.max(1, Math.ceil((oldest + window.toMillis() - now) / 1000.0));
                throw ApiException.tooManyRequests(message, retryAfter);
            }
            q.addLast(now);
        }
    }

    private static String sanitize(String key) {
        if (key == null || key.isBlank()) {
            return "unknown";
        }
        return key.length() > 120 ? key.substring(0, 120) : key;
    }
}
