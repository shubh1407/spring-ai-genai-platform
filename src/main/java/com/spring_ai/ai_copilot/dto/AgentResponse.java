package com.spring_ai.ai_copilot.dto;

import java.util.List;

public record AgentResponse(String message, List<String> toolsUsed, List<Citation> sources) {
}
