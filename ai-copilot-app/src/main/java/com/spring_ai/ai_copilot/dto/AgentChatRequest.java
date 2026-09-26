package com.spring_ai.ai_copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(
        @NotBlank(message = "Conversation Id can't be blank")
        String conversationId,
        @NotBlank(message = "Message can't be blank")
        @Size(max = 1000, message = "Message can't be longer than 1000 characters")
        String message,
        @NotBlank(message = "tenant id can't be blank")
        @Size(max = 1000, message = "tenant id can't be longer than 1000 characters")
        String tenantId) {
}
