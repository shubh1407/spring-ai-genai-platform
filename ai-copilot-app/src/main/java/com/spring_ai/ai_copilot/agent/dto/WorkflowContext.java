package com.spring_ai.ai_copilot.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class WorkflowContext
{
    private String originalRequest;
    private String conversationId;
    private List<SpecialistResult> previousResults;
    private List<String> currentTasks;
    private WorkflowStatus workflowStatus;
    private String tenantId;
}
