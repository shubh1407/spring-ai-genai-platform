package com.spring_ai.ai_copilot.approval.controller;

import com.spring_ai.ai_copilot.agent.services.Orchestrator;
import com.spring_ai.ai_copilot.approval.dto.ApprovalDecisionRequest;
import com.spring_ai.ai_copilot.approval.dto.ApprovalRequest;
import com.spring_ai.ai_copilot.approval.dto.ApprovalStatus;
import com.spring_ai.ai_copilot.approval.dto.CreateApprovalRequest;
import com.spring_ai.ai_copilot.approval.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    private final Orchestrator orchestrator;

    public ApprovalController(ApprovalService approvalService, Orchestrator orchestrator) {
        this.approvalService = approvalService;
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ApprovalRequest create(
            @Valid @RequestBody CreateApprovalRequest request
    ) {
        return approvalService.create(request);
    }

    @GetMapping("/pending")
    public List<ApprovalRequest> getPending(
            @RequestHeader("X-Approver-Id") String approverId
    ) {
        return approvalService.getPending(approverId);
    }

    @GetMapping("/{approvalRequestId}")
    public ApprovalRequest getById(
            @PathVariable String approvalRequestId
    ) {
        return approvalService.getById(approvalRequestId);
    }

    @PostMapping("/{approvalRequestId}/decision")
    public ApprovalRequest decide(
            @PathVariable String approvalRequestId,
            @RequestHeader("X-Approver-Id") String approverId,
            @Valid @RequestBody ApprovalDecisionRequest request) {

        ApprovalRequest updated = approvalService.decide(
                approvalRequestId,
                approverId,
                request
        );

        if (updated.status() == ApprovalStatus.APPROVED) {
            orchestrator.resumeAfterApproval(updated.workflowId());
        }

        return updated;
    }
}
