package com.spring_ai.ai_copilot.approval.service;

import com.spring_ai.ai_copilot.agent.dto.WorkflowContext;
import com.spring_ai.ai_copilot.agent.dto.WorkflowStatus;
import com.spring_ai.ai_copilot.agent.services.WorkflowStore;
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
    private final WorkflowStore workflowStore;

    public ApprovalService(ApprovalStore approvalStore,
                          WorkflowStore workflowStore) {
        this.approvalStore = approvalStore;
        this.workflowStore = workflowStore;
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
        workflowStore.findByWorkflowId(input.workflowId())
                .ifPresent(context -> {
                    context.setPendingApprovalRequestId(request.approvalRequestId());
                    workflowStore.save(context);
                });

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
            ApprovalDecisionRequest input) {

        ApprovalRequest updated = approvalStore.decide(
                approvalRequestId,
                approverId,
                input.decision(),
                input.comments()
        );

        workflowStore.findByWorkflowId(updated.workflowId())
                .ifPresent(context -> {

                    if (updated.status() == ApprovalStatus.APPROVED) {
                        context.setWorkflowStatus(WorkflowStatus.IN_PROGRESS);
                        context.setErrorMessage(null);

                    } else if (updated.status() == ApprovalStatus.REJECTED) {
                        context.setWorkflowStatus(WorkflowStatus.REJECTED);
                        context.setErrorMessage("Approval rejected by approver.");
                        context.setFinalResult("Protected action was rejected.");
                    }

                    workflowStore.save(context);
                });

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
