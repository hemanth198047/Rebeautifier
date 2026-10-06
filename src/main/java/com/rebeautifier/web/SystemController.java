package com.rebeautifier.web;

import com.rebeautifier.domain.User;
import com.rebeautifier.service.AuthService;
import com.rebeautifier.service.RateLimitService;
import com.rebeautifier.web.dto.RateLimitStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public SystemController(AuthService authService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/rate-limits")
    public RateLimitStatusResponse rateLimits(HttpServletRequest request) {
        User user = authService.requireCurrentUser();
        String client = clientKey(request);
        RateLimitService.Usage auth = rateLimitService.peekAuth(client);
        RateLimitService.Usage ai = rateLimitService.peekAi(user.getId().toString());
        return new RateLimitStatusResponse(
                "in-memory",
                RateLimitService.WINDOW_SECONDS,
                rateLimitService.authPerMinute(),
                auth.used(),
                auth.remaining(),
                rateLimitService.aiPerMinute(),
                ai.used(),
                ai.remaining(),
                "Limits are per API process and reset when the process restarts. They are not shared across replicas."
        );
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
