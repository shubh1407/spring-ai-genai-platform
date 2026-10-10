package com.spring_ai.ai_copilot.agent.dto;

public record OrchestratorResponse(
        WorkflowStatus status,
        String questionToUser,
        SpecialistResult result,
        String workflowId,
        String conversationId,
        String approvalRequestId
) {

    public OrchestratorResponse(WorkflowStatus status, String questionToUser, SpecialistResult result) {
        this(status, questionToUser, result, null, null, null);
    }

    public static OrchestratorResponse waitingForUser(
            String questionToUser) {

        return new OrchestratorResponse(
                WorkflowStatus.WAITING_FOR_USER_INPUT,
                questionToUser,
                null,
                null,
                null,
                null
        );
    }

    public static OrchestratorResponse completed(
            SpecialistResult result,
            String workflowId,
            String conversationId) {

        return new OrchestratorResponse(
                WorkflowStatus.COMPLETED,
                null,
                result,
                workflowId,
                conversationId,
                null
        );
    }

    public static OrchestratorResponse completed(
            SpecialistResult result) {
        return completed(result, null, null);
    }

    public static OrchestratorResponse failed(
            String message) {

        return new OrchestratorResponse(
                WorkflowStatus.FAILED,
                message,
                null,
                null,
                null,
                null
        );
    }

    public static OrchestratorResponse waitForApproval(
            String message,
            String workflowId,
            String conversationId,
            String approvalRequestId) {

        return new OrchestratorResponse(
                WorkflowStatus.WAITING_FOR_APPROVAL,
                message,
                null,
                workflowId,
                conversationId,
                approvalRequestId
        );
    }

    public static OrchestratorResponse waitingForApproval(
            String message,
            String workflowId,
            String conversationId,
            String approvalRequestId) {
        return waitForApproval(message, workflowId, conversationId, approvalRequestId);
    }
}