package com.spring_ai.ai_copilot.exception;

import org.springframework.http.HttpStatus;

public class InvalidSpecialistResultException extends AiDomainException {

    public InvalidSpecialistResultException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "SPECIALIST_RESULT_INVALID", message);
    }

    public InvalidSpecialistResultException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "SPECIALIST_RESULT_INVALID", message, cause);
    }

    public InvalidSpecialistResultException() {
        this("Specialist returned an invalid result");
    }
}
