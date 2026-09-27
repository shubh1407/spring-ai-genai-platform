package com.spring_ai.ai_copilot.dto;

import java.util.List;

public record AgentPlan(String goal, List<PlanStep> steps) {
}
