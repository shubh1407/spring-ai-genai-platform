package com.spring_ai.ai_copilot.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApprovalRequest(
        @NotBlank String workflowId,
        @NotBlank String conversationId,
        @NotBlank String actionType,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank String approverId
) {
}
