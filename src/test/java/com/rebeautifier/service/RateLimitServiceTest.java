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
}
