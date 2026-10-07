package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.*;
import com.spring_ai.ai_copilot.agent.specialists.Specialist;
import com.spring_ai.ai_copilot.agent.specialists.SpecialistResolver;
import com.spring_ai.ai_copilot.exception.AiDomainException;
import com.spring_ai.ai_copilot.exception.AiProviderException;
import com.spring_ai.ai_copilot.exception.SpecialistExecutionException;
import com.spring_ai.ai_copilot.exception.ToolValidationException;
import com.spring_ai.ai_copilot.exception.UnsupportedDocumentTypeException;
import org.springframework.stereotype.Service;

@Service
public class SpecialistService {

    private final SpecialistResolver specialistResolver;

    public SpecialistService(SpecialistResolver specialistResolver) {
        this.specialistResolver = specialistResolver;
    }

    public SpecialistResult getSpecialist(SpecialistTask specialistTask, WorkflowContext workflowContext) {
        SpecialistRequest specialistRequest = new SpecialistRequest(
                workflowContext.getConversationId(),
                workflowContext.getTenantId(),
                specialistTask.task(),
                workflowContext.getOriginalRequest(),
                workflowContext
        );

        try {
            Specialist specialist = specialistResolver.getSpecialistAgent(specialistTask.specialistsType());
            return specialist.execute(specialistRequest, workflowContext.getConversationId());
        } catch (AiDomainException | AiProviderException | ToolValidationException | UnsupportedDocumentTypeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SpecialistExecutionException(
                    "Specialist execution failed for type " + specialistTask.specialistsType(),
                    e
            );
        }
    }
}
