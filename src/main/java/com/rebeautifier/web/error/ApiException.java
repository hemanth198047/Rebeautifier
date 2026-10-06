package com.rebeautifier.web.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
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
}
