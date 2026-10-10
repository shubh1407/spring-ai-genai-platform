package com.spring_ai.ai_copilot.controller;

import com.spring_ai.ai_copilot.agent.dto.WorkflowContext;
import com.spring_ai.ai_copilot.agent.dto.WorkflowStatus;
import com.spring_ai.ai_copilot.agent.services.WorkflowStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkflowController {

    private final WorkflowStore workflowStore;

    public WorkflowController(WorkflowStore workflowStore) {
        this.workflowStore = workflowStore;
    }

    @GetMapping("/workflows/{workflowId}")
    public ResponseEntity<WorkflowStatusResponse> getWorkflow(@PathVariable String workflowId) {
        WorkflowContext context = workflowStore.findByWorkflowId(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        return ResponseEntity.ok(new WorkflowStatusResponse(
                context.getWorkflowId(),
                context.getConversationId(),
                context.getWorkflowStatus(),
                context.getPendingApprovalRequestId(),
                context.getFinalResult(),
                context.getErrorMessage()
        ));
    }

    public record WorkflowStatusResponse(
            String workflowId,
            String conversationId,
            WorkflowStatus status,
            String approvalRequestId,
            String result,
            String error
    ) {
    }
}
