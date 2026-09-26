package com.spring_ai.ai_copilot.dto;

public record PlanStep(
        int stepNumber,
        String action,
        String purpose
) {
}
