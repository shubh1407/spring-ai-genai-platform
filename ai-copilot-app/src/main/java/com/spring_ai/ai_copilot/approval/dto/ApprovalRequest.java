package com.spring_ai.ai_copilot.approval.dto;

import java.time.Instant;

public record ApprovalRequest(
        String approvalRequestId,
        String workflowId,
        String conversationId,
        String actionType,
        String description,
        String approverId,
        ApprovalStatus status,
        Instant createdAt,
        Instant decidedAt,
        String decisionComments
) {

    public ApprovalRequest withDecision(
            ApprovalStatus newStatus,
            Instant decisionTime,
            String comments
    ) {
        return new ApprovalRequest(
                approvalRequestId,
                workflowId,
                conversationId,
                actionType,
                description,
                approverId,
                newStatus,
                createdAt,
                decisionTime,
                comments
        );
    }
}
