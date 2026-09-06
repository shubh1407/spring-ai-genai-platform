package com.spring_ai.ai_copilot.dto;

import java.util.List;

public record RagResponse(String message, List<Citation> sources) {
}
