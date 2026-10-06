package com.rebeautifier.web.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Integer retryAfterSeconds;

    public ApiException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public ApiException(String code, String message, HttpStatus status, Integer retryAfterSeconds) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public static ApiException notFound(String message) {
        return new ApiException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static ApiException badRequest(String message) {
        return new ApiException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    public static ApiException conflict(String message) {
        return new ApiException("CONFLICT", message, HttpStatus.CONFLICT);
    }

    public static ApiException tooManyRequests(String message, int retryAfterSeconds) {
        return new ApiException("RATE_LIMITED", message, HttpStatus.TOO_MANY_REQUESTS, retryAfterSeconds);
    }
}
