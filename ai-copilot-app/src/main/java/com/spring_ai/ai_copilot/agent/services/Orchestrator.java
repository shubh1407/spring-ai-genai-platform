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

    public SpecialistResult orchestrate(AgentChatRequest request){

        WorkflowContext context = new WorkflowContext(request.message(),request.conversationId(), new ArrayList<>(),null, WorkflowStatus.IN_PROGRESS);
        SupervisorDecision supervisorDecision = agentService.getSupervisorResponse(context);
        SpecialistResult specialistResult;
        while(supervisorDecision!=null && !supervisorDecision.action().equals(Action.FINISH)){
            context.setCurrentTask(supervisorDecision.task());
            specialistResult=specialistService.getSpecialist(supervisorDecision,context);
            context.getPreviousResults().add(specialistResult);
            supervisorDecision = agentService.getSupervisorResponse(context);
        }
        context.setWorkflowStatus(WorkflowStatus.COMPLETED);
        return context.getPreviousResults().getLast();
    }
}
