package com.spring_ai.ai_copilot.exception;

import com.spring_ai.ai_copilot.agent.dto.SpecialistsType;
import org.springframework.http.HttpStatus;

public class SpecialistNotFoundException extends AiDomainException {

    public SpecialistNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "SPECIALIST_NOT_FOUND", message);
    }

    public SpecialistNotFoundException(String message, Throwable cause) {
        super(HttpStatus.NOT_FOUND, "SPECIALIST_NOT_FOUND", message, cause);
    }

    public SpecialistNotFoundException(SpecialistsType specialistsType) {
        this("No specialist found for requested specialist type: " + specialistsType);
    }
}
