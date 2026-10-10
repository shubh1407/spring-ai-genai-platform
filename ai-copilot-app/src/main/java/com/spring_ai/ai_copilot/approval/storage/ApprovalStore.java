package com.spring_ai.ai_copilot.approval.storage;

import com.spring_ai.ai_copilot.approval.dto.ApprovalDecision;
import com.spring_ai.ai_copilot.approval.dto.ApprovalRequest;
import com.spring_ai.ai_copilot.approval.dto.ApprovalStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ApprovalStore {

    private final ConcurrentMap<String, ApprovalRequest> approvals =
            new ConcurrentHashMap<>();

    public ApprovalRequest save(ApprovalRequest request) {
        approvals.put(request.approvalRequestId(), request);
        return request;
    }

    public Optional<ApprovalRequest> findById(String approvalRequestId) {
        return Optional.ofNullable(approvals.get(approvalRequestId));
    }

    public List<ApprovalRequest> findPendingForApprover(String approverId) {
        return approvals.values()
                .stream()
                .filter(request ->
                        request.approverId().equals(approverId))
                .filter(request ->
                        request.status() == ApprovalStatus.PENDING)
                .toList();
    }

    public List<ApprovalRequest> findAll() {
        return List.copyOf(approvals.values());
    }

    public synchronized ApprovalRequest decide(
            String approvalRequestId,
            String approverId,
            ApprovalDecision decision,
            String comments
    ) {
        ApprovalRequest current = approvals.get(approvalRequestId);

        if (current == null) {
            throw new IllegalArgumentException(
                    "Approval request not found: " + approvalRequestId);
        }

        if (!current.approverId().equals(approverId)) {
            throw new SecurityException(
                    "You are not authorized to decide this approval");
        }

        if (current.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException(
                    "Approval has already been decided");
        }

        ApprovalStatus newStatus =
                decision == ApprovalDecision.APPROVED
                        ? ApprovalStatus.APPROVED
                        : ApprovalStatus.REJECTED;

        ApprovalRequest updated = current.withDecision(
                newStatus,
                java.time.Instant.now(),
                comments
        );

        approvals.put(approvalRequestId, updated);
        return updated;
    }
}
