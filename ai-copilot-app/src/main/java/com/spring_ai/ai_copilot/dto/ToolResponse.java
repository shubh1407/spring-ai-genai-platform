package com.spring_ai.ai_copilot.dto;

import org.springframework.ai.document.Document;

import java.util.List;

public record ToolResponse(List<Document> documents, List<Citation> sources) {
}
