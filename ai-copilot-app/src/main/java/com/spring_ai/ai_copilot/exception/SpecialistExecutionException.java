package com.spring_ai.ai_copilot.exception;

import org.springframework.http.HttpStatus;

public class SpecialistExecutionException extends AiDomainException {

    public SpecialistExecutionException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "SPECIALIST_EXECUTION_ERROR", message);
    }

    public SpecialistExecutionException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "SPECIALIST_EXECUTION_ERROR", message, cause);
    }

    public SpecialistExecutionException(Throwable cause) {
        this("Specialist execution failed", cause);
    }
}
