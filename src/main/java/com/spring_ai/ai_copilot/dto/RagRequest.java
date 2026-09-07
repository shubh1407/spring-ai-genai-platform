package com.spring_ai.ai_copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RequestParam;

public record RagRequest(
        @NotBlank(message = "Conversation Id can't be blank")
        String conversationId,
        @NotBlank(message = "Question can't be blank")
        @Size(max = 1000, message = "Question can't be longer than 1000 characters")
        String question,
        @NotBlank(message = "Tenant Id can't be blank")
        String tenantId) {
}
