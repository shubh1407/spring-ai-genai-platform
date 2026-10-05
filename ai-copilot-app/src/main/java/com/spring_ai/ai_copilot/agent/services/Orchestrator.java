package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.*;
import com.spring_ai.ai_copilot.agent.tools.TenantContext;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class Orchestrator {

    private final SpecialistService specialistService;

    private final AgentService agentService;

    private final TenantContext tenantContext;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public Orchestrator(SpecialistService specialistService, AgentService agentService, TenantContext tenantContext) {
        this.specialistService = specialistService;
        this.agentService = agentService;
        this.tenantContext = tenantContext;
    }

    public OrchestratorResponse orchestrate(AgentChatRequest request){

        WorkflowContext context = new WorkflowContext(request.message(),request.conversationId(), new ArrayList<>(),null, WorkflowStatus.IN_PROGRESS,tenantContext.getTenantId());
        SupervisorDecision supervisorDecision = agentService.getSupervisorResponse(context);
        List<SpecialistResult> specialistResults;
        int maxIterations = 10;
        int iterations = 0;
        while(supervisorDecision!=null
                && supervisorDecision.action().equals(Action.DELEGATE)
                && iterations < maxIterations){
            context.setCurrentTasks(supervisorDecision.tasks().stream().map(SpecialistTask::task).toList());
            specialistResults=executeParallel(supervisorDecision.tasks(),context);
            context.getPreviousResults().addAll(specialistResults);
            supervisorDecision = agentService.getSupervisorResponse(context);
            iterations++;
        }
        if(supervisorDecision!=null && supervisorDecision.action().equals(Action.ASK_USER)){
            context.setWorkflowStatus(WorkflowStatus.WAITING_FOR_USER_INPUT);
            return OrchestratorResponse.waitingForUser(
                    supervisorDecision.questionToUser()
            );
        }
        if(supervisorDecision!=null && supervisorDecision.action().equals(Action.DELEGATE)){
            context.setWorkflowStatus(WorkflowStatus.FAILED);
            return OrchestratorResponse.failed(
                    "Maximum workflow iterations exceeded"
            );
        }
        context.setWorkflowStatus(WorkflowStatus.COMPLETED);
        assert supervisorDecision != null;
        return OrchestratorResponse.completed(
                new SpecialistResult(
                        SpecialistsType.REPORT,
                        "SUCCESS",
                        supervisorDecision.finalResponse(),
                        null
                )
        );
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
