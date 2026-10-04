package com.spring_ai.ai_copilot.agent.dto;

public record SupervisorDecision(Action action,SpecialistsType specialistsType,String task,String reason,String originalMessage,String finalResponse) {
}
