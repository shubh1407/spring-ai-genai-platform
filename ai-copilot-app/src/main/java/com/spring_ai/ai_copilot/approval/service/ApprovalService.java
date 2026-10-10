package com.spring_ai.ai_copilot.approval.service;

import com.spring_ai.ai_copilot.approval.dto.ApprovalDecisionRequest;
import com.spring_ai.ai_copilot.approval.dto.ApprovalRequest;
import com.spring_ai.ai_copilot.approval.dto.ApprovalStatus;
import com.spring_ai.ai_copilot.approval.dto.CreateApprovalRequest;
import com.spring_ai.ai_copilot.approval.storage.ApprovalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ApprovalService {

    private static final Logger log =
            LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalStore approvalStore;

    public ApprovalService(ApprovalStore approvalStore) {
        this.approvalStore = approvalStore;
    }

    public ApprovalRequest create(CreateApprovalRequest input) {
        ApprovalRequest request = new ApprovalRequest(
                UUID.randomUUID().toString(),
                input.workflowId(),
                input.conversationId(),
                input.actionType(),
                input.description(),
                input.approverId(),
                ApprovalStatus.PENDING,
                Instant.now(),
                null,
                null
        );

        approvalStore.save(request);

        log.info(
                "APPROVAL_REQUESTED approvalId={} workflowId={} conversationId={}",
                request.approvalRequestId(),
                request.workflowId(),
                request.conversationId()
        );

        return request;
    }

    public List<ApprovalRequest> getPending(String approverId) {
        return approvalStore.findPendingForApprover(approverId);
    }

    public ApprovalRequest getById(String approvalRequestId) {
        return approvalStore.findById(approvalRequestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Approval request not found: " + approvalRequestId));
    }

    public ApprovalRequest decide(
            String approvalRequestId,
            String approverId,
            ApprovalDecisionRequest input
    ) {
        ApprovalRequest updated = approvalStore.decide(
                approvalRequestId,
                approverId,
                input.decision(),
                input.comments()
        );

        log.info(
                "APPROVAL_DECISION approvalId={} workflowId={} decision={} approverId={}",
                updated.approvalRequestId(),
                updated.workflowId(),
                updated.status(),
                approverId
        );

        return updated;
    }
}
