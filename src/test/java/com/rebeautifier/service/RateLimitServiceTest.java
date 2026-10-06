package com.rebeautifier.service;

import com.rebeautifier.web.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {

    @Test
    void authLimitReturns429WithRetryAfter() {
        RateLimitService service = new RateLimitService(2, 5);
        service.checkAuth("client-a");
        service.checkAuth("client-a");
        assertThatThrownBy(() -> service.checkAuth("client-a"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(api.getRetryAfterSeconds()).isNotNull().isGreaterThan(0);
                });
        // other clients unaffected
        service.checkAuth("client-b");
    }

    @Test
    void peekDoesNotConsumeQuota() {
        RateLimitService service = new RateLimitService(3, 4);
        assertThat(service.peekAuth("client-x").used()).isZero();
        assertThat(service.peekAuth("client-x").remaining()).isEqualTo(3);
        service.checkAuth("client-x");
        RateLimitService.Usage after = service.peekAuth("client-x");
        assertThat(after.used()).isEqualTo(1);
        assertThat(after.remaining()).isEqualTo(2);
        assertThat(service.peekAuth("client-x").used()).isEqualTo(1);
        assertThat(service.aiPerMinute()).isEqualTo(4);
        assertThat(service.authPerMinute()).isEqualTo(3);
    }
}
