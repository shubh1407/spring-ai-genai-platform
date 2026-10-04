package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.*;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class Orchestrator {

    private final SpecialistService specialistService;

    private final AgentService agentService;

    public Orchestrator(SpecialistService specialistService, AgentService agentService) {
        this.specialistService = specialistService;
        this.agentService = agentService;
    }

    public OrchestratorResponse orchestrate(AgentChatRequest request){

        WorkflowContext context = new WorkflowContext(request.message(),request.conversationId(), new ArrayList<>(),null, WorkflowStatus.IN_PROGRESS);
        SupervisorDecision supervisorDecision = agentService.getSupervisorResponse(context);
        SpecialistResult specialistResult;
        int maxIterations = 10;
        int iterations = 0;
        while(supervisorDecision!=null
                && supervisorDecision.action().equals(Action.DELEGATE)
                && iterations < maxIterations){
            context.setCurrentTask(supervisorDecision.task());
            specialistResult=specialistService.getSpecialist(supervisorDecision,context);
            context.getPreviousResults().add(specialistResult);
            supervisorDecision = agentService.getSupervisorResponse(context);
            iterations++;
        }
        if(supervisorDecision!=null && supervisorDecision.action().equals(Action.ASK_USER)){
            context.setWorkflowStatus(WorkflowStatus.WAITING_FOR_USER_INPUT);
            return OrchestratorResponse.waitingForUser(
                    supervisorDecision.task()
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
}
