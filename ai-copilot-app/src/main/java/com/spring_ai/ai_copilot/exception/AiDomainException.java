package com.spring_ai.ai_copilot.exception;

import org.springframework.http.HttpStatus;

public class AiDomainException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final String message;

    public AiDomainException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public AiDomainException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
