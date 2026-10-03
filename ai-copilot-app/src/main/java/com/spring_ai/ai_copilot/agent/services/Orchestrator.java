package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.Action;
import com.spring_ai.ai_copilot.agent.dto.SpecialistResult;
import com.spring_ai.ai_copilot.agent.dto.SupervisorContext;
import com.spring_ai.ai_copilot.agent.dto.SupervisorDecision;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;

@Service
public class Orchestrator {

    private final SpecialistService specialistService;

    private final AgentService agentService;

    public Orchestrator(SpecialistService specialistService, AgentService agentService) {
        this.specialistService = specialistService;
        this.agentService = agentService;
    }

    public SpecialistResult orchestrate(AgentChatRequest request){

        SupervisorContext context = new SupervisorContext(request.message(),request.conversationId(), new ArrayList<>());
        SupervisorDecision supervisorDecision = agentService.getSupervisorResponse(context);
        SpecialistResult specialistResult=null;
        while(supervisorDecision!=null && !supervisorDecision.action().equals(Action.FINISH)){
            specialistResult=specialistService.getSpecialist(supervisorDecision,request.conversationId());
            context.previousResults().add(specialistResult);
            supervisorDecision = agentService.getSupervisorResponse(context);
        }
        return context.previousResults().getLast();
    }
}
