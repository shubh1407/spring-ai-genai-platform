package com.spring_ai.ai_copilot.agent.dto;

public record SpecialistRequest(String conversationId,String tenantId,String task,String originalMessage,WorkflowContext context) {
}
