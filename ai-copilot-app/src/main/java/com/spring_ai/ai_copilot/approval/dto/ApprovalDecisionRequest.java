package com.spring_ai.ai_copilot.approval.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(@NotNull ApprovalDecision decision,
                                      @Size(max = 500) String comments) {
}
