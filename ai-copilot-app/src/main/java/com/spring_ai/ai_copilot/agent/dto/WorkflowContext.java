package com.spring_ai.ai_copilot.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class WorkflowContext
{
    private String workflowId;
    private String originalRequest;
    private String conversationId;
    private List<SpecialistResult> previousResults;
    private List<String> currentTasks;
    private WorkflowStatus workflowStatus;
    private String tenantId;
    private String pendingApprovalRequestId;
    private String pendingProtectedAction;
    private String finalResult;
    private String errorMessage;

    public WorkflowContext(String originalRequest, String conversationId, List<SpecialistResult> previousResults,
                           List<String> currentTasks, WorkflowStatus workflowStatus, String tenantId) {
        this(null, originalRequest, conversationId, previousResults, currentTasks, workflowStatus, tenantId,
                null, null, null, null);
    }
}
