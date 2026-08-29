package com.spring_ai.ai_copilot.exception;

import org.springframework.http.HttpStatus;

public class AiProviderException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AiProviderException(
            HttpStatus status,
            String errorCode,
            String message,
            Throwable cause) {

        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}