package com.spring_ai.ai_copilot.agent.dto;

import java.util.List;

public record SupervisorContext(
        String originalRequest,
        String conversationId,
        List<SpecialistResult> previousResults
) {}
