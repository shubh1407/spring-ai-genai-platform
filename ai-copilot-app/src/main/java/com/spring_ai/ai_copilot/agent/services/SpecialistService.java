package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.SpecialistRequest;
import com.spring_ai.ai_copilot.agent.dto.SpecialistResult;
import com.spring_ai.ai_copilot.agent.dto.SupervisorDecision;
import com.spring_ai.ai_copilot.agent.dto.WorkflowContext;
import com.spring_ai.ai_copilot.agent.specialists.Specialist;
import com.spring_ai.ai_copilot.agent.specialists.SpecialistResolver;
import com.spring_ai.ai_copilot.agent.tools.TenantContext;
import com.spring_ai.ai_copilot.exception.AiDomainException;
import com.spring_ai.ai_copilot.exception.AiProviderException;
import com.spring_ai.ai_copilot.exception.SpecialistExecutionException;
import com.spring_ai.ai_copilot.exception.ToolValidationException;
import com.spring_ai.ai_copilot.exception.UnsupportedDocumentTypeException;
import org.springframework.stereotype.Service;

@Service
public class SpecialistService {

    private final SpecialistResolver specialistResolver;
    private final TenantContext tenantContext;

    public SpecialistService(SpecialistResolver specialistResolver, TenantContext tenantContext) {
        this.specialistResolver = specialistResolver;
        this.tenantContext = tenantContext;
    }

    public SpecialistResult getSpecialist(SupervisorDecision supervisorDecision, WorkflowContext workflowContext) {
        SpecialistRequest specialistRequest = new SpecialistRequest(
                workflowContext.getConversationId(),
                tenantContext.getTenantId(),
                supervisorDecision.task(),
                workflowContext.getOriginalRequest(),
                workflowContext
        );

        try {
            Specialist specialist = specialistResolver.getSpecialistAgent(supervisorDecision.specialistsType());
            return specialist.execute(specialistRequest, workflowContext.getConversationId());
        } catch (AiDomainException | AiProviderException | ToolValidationException | UnsupportedDocumentTypeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SpecialistExecutionException(
                    "Specialist execution failed for type " + supervisorDecision.specialistsType(),
                    e
            );
        }
    }
}
