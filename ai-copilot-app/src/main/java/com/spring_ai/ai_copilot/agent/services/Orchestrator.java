package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.*;
import com.spring_ai.ai_copilot.agent.tools.TenantContext;
import com.spring_ai.ai_copilot.approval.dto.ApprovalRequest;
import com.spring_ai.ai_copilot.approval.dto.CreateApprovalRequest;
import com.spring_ai.ai_copilot.approval.service.ApprovalService;
import com.spring_ai.ai_copilot.approval.service.MockProtectedActionService;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final SpecialistService specialistService;
    private final AgentService agentService;
    private final TenantContext tenantContext;
    private final ApprovalService approvalService;
    private final WorkflowStore workflowStore;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final MockProtectedActionService mockProtectedActionService;

    public Orchestrator(SpecialistService specialistService, AgentService agentService,
                        TenantContext tenantContext, ApprovalService approvalService,
                        WorkflowStore workflowStore, MockProtectedActionService mockProtectedActionService) {
        this.specialistService = specialistService;
        this.agentService = agentService;
        this.tenantContext = tenantContext;
        this.approvalService = approvalService;
        this.workflowStore = workflowStore;
        this.mockProtectedActionService = mockProtectedActionService;
    }

    public OrchestratorResponse orchestrate(AgentChatRequest request) {
        String workflowId = UUID.randomUUID().toString();
        WorkflowContext context = new WorkflowContext(
                workflowId,
                request.message(),
                request.conversationId(),
                new ArrayList<>(),
                null,
                WorkflowStatus.IN_PROGRESS,
                tenantContext.getTenantId(),
                null,
                null,
                null,
                null
        );
        workflowStore.save(context);

        SupervisorDecision supervisorDecision = agentService.getSupervisorResponse(context);
        List<SpecialistResult> specialistResults;
        int maxIterations = 10;
        int iterations = 0;
        while (supervisorDecision != null
                && supervisorDecision.action().equals(Action.DELEGATE)
                && iterations < maxIterations) {
            context.setCurrentTasks(supervisorDecision.tasks().stream().map(SpecialistTask::task).toList());
            specialistResults = executeParallel(supervisorDecision.tasks(), context);
            context.getPreviousResults().addAll(specialistResults);
            supervisorDecision = agentService.getSupervisorResponse(context);
            iterations++;
        }
        if (supervisorDecision != null && supervisorDecision.action().equals(Action.ASK_USER)) {
            context.setWorkflowStatus(WorkflowStatus.WAITING_FOR_USER_INPUT);
            workflowStore.save(context);
            return OrchestratorResponse.waitingForUser(supervisorDecision.questionToUser());
        }
        if (supervisorDecision != null
                && supervisorDecision.action() == Action.WAIT_FOR_APPROVAL) {

            context.setPendingProtectedAction("SEND_REPORT");
            context.setWorkflowStatus(WorkflowStatus.WAITING_FOR_APPROVAL);

            ApprovalRequest approvalRequest = approvalService.create(
                    new CreateApprovalRequest(
                            context.getWorkflowId(),
                            context.getConversationId(),
                            "SEND_REPORT",
                            supervisorDecision.questionToUser(),
                            "approver-1"
                    )
            );

            context.setPendingApprovalRequestId(
                    approvalRequest.approvalRequestId()
            );

            workflowStore.save(context);

            return OrchestratorResponse.waitForApproval(
                    supervisorDecision.questionToUser(),
                    context.getWorkflowId(),
                    context.getConversationId(),
                    approvalRequest.approvalRequestId()
            );
        }

        if (supervisorDecision != null && supervisorDecision.action().equals(Action.DELEGATE)) {
            context.setWorkflowStatus(WorkflowStatus.FAILED);
            workflowStore.save(context);
            return OrchestratorResponse.failed("Maximum workflow iterations exceeded");
        }
        context.setWorkflowStatus(WorkflowStatus.COMPLETED);
        assert supervisorDecision != null;
        workflowStore.save(context);
        return OrchestratorResponse.completed(
                new SpecialistResult(
                        SpecialistsType.REPORT,
                        "SUCCESS",
                        supervisorDecision.finalResponse(),
                        null
                ),
                context.getWorkflowId(),
                context.getConversationId()
        );
    }

    public WorkflowContext resumeAfterApproval(String workflowId) {
        WorkflowContext context = workflowStore.findByWorkflowId(workflowId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Workflow not found: " + workflowId
                        ));

        if (context.getWorkflowStatus() != WorkflowStatus.WAITING_FOR_APPROVAL) {
            throw new IllegalStateException(
                    "Workflow is not waiting for approval"
            );
        }

        if (context.getPendingProtectedAction() == null) {
            throw new IllegalStateException("No protected action is pending");
        }

        try {
            context.setWorkflowStatus(WorkflowStatus.IN_PROGRESS);
            workflowStore.save(context);

            // Execute the protected action only after approval.
            String result = mockProtectedActionService.execute(context);

            context.getPreviousResults().add(
                    new SpecialistResult(
                            SpecialistsType.REPORT,
                            "SUCCESS",
                            result,
                            null
                    )
            );

            context.setFinalResult(result);
            context.setPendingProtectedAction(null);
            context.setPendingApprovalRequestId(null);
            workflowStore.save(context);

            // Resume the AI workflow using the saved context.
            SupervisorDecision decision = agentService.getSupervisorResponse(context);

            int iterations = 0;
            int maxIterations = 10;

            while (decision != null
                    && decision.action() == Action.DELEGATE
                    && iterations < maxIterations) {

                context.setCurrentTasks(
                        decision.tasks().stream()
                                .map(SpecialistTask::task)
                                .toList()
                );

                List<SpecialistResult> results =
                        executeParallel(decision.tasks(), context);

                context.getPreviousResults().addAll(results);

                decision = agentService.getSupervisorResponse(context);
                iterations++;
            }

            if (decision == null) {
                throw new IllegalStateException(
                        "Supervisor returned no decision after approval"
                );
            }

            if (decision.action() == Action.ASK_USER) {
                context.setWorkflowStatus(WorkflowStatus.WAITING_FOR_USER_INPUT);
            } else if (decision.action() == Action.DELEGATE) {
                throw new IllegalStateException(
                        "Maximum workflow iterations exceeded"
                );
            } else if (decision.action() == Action.WAIT_FOR_APPROVAL) {
                context.setPendingProtectedAction("SEND_REPORT");

                ApprovalRequest approvalRequest = approvalService.create(
                        new CreateApprovalRequest(
                                context.getWorkflowId(),
                                context.getConversationId(),
                                "SEND_REPORT",
                                decision.questionToUser(),
                                "approver-1"
                        )
                );

                context.setPendingApprovalRequestId(
                        approvalRequest.approvalRequestId()
                );
                context.setWorkflowStatus(WorkflowStatus.WAITING_FOR_APPROVAL);
            } else {
                context.setFinalResult(decision.finalResponse());
                context.setWorkflowStatus(WorkflowStatus.COMPLETED);
            }

        } catch (Exception e) {
            context.setErrorMessage(e.getMessage());
            context.setWorkflowStatus(WorkflowStatus.FAILED);
            log.error("Workflow resume failed. workflowId={}", workflowId, e);
        }

        workflowStore.save(context);
        return context;
    }

    private boolean requiresApproval(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toUpperCase();
        return normalized.contains("SEND_REPORT") || normalized.contains("REQUEST APPROVAL FOR THE MOCK SEND_REPORT ACTION");
    }

    private List<SpecialistResult> executeParallel(
            List<SpecialistTask> tasks,
            WorkflowContext context
    ) {

        List<CompletableFuture<SpecialistResult>> futures = tasks.stream()
                .map(task ->
                        CompletableFuture.supplyAsync(
                                () -> executeTask(task, context),
                                executor
                        )
                )
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private SpecialistResult executeTask(
            SpecialistTask delegatedTask,
            WorkflowContext context
    ) {
        return specialistService.getSpecialist(
                delegatedTask,
                context
        );
    }
}
