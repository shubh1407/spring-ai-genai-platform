package com.spring_ai.ai_copilot.agent.specialists.impl;

import com.spring_ai.ai_copilot.agent.dto.SpecialistRequest;
import com.spring_ai.ai_copilot.agent.dto.SpecialistResult;
import com.spring_ai.ai_copilot.agent.dto.SpecialistsType;
import com.spring_ai.ai_copilot.agent.specialists.Specialist;
import com.spring_ai.ai_copilot.agent.tools.KnowledgeSearchTool;
import com.spring_ai.ai_copilot.agent.tools.PlannerTool;
import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.exception.AiDomainException;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import com.spring_ai.ai_copilot.exception.AiProviderException;
import com.spring_ai.ai_copilot.exception.InvalidSpecialistResultException;
import com.spring_ai.ai_copilot.exception.ToolValidationException;
import com.spring_ai.ai_copilot.exception.UnsupportedDocumentTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.spring_ai.ai_copilot.constant.PromptsConstants.KNOWLEDGE_SPECIALIST_SYSTEM_PROMPT;

@Service
public class KnowledgeSpecialists implements Specialist {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSpecialists.class);

    private final AiProviderClient client;
    private final AiExceptionTranslator aiExceptionTranslator;
    private final KnowledgeSearchTool knowledgeSearchTool;
    private final PlannerTool plannerTool;

    public KnowledgeSpecialists(AiProviderClient client, AiExceptionTranslator aiExceptionTranslator, KnowledgeSearchTool knowledgeSearchTool, PlannerTool plannerTool) {
        this.client = client;
        this.aiExceptionTranslator = aiExceptionTranslator;
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.plannerTool = plannerTool;
    }

    @Override
    public SpecialistResult execute(SpecialistRequest chatRequest, String conversationId) {
        log.info("Knowledge Specialists agent is called for conversation id {},task {}, tenantId {}", chatRequest.conversationId(), chatRequest.task(), chatRequest.tenantId());
        return getKnowledgeSearch(chatRequest, conversationId);
    }

    private SpecialistResult getKnowledgeSearch(SpecialistRequest chatRequest, String conversationId) {
        log.info("service.agent.response.started operation=get.knowledge.Search");
        try {
            PromptTemplate promptTemplate = PromptTemplate.builder()
                    .template("""
                            You are executing a specific task delegated by the Supervisor.
                            
                            Original User Request:
                            {message}
           
                            Current Delegated Task:
                            {task}
           
                            Previous Specialist Results:
                            {previousResults}
           
                            Execute ONLY the current delegated task.
           
                            Use previous specialist results when they are relevant to the current task.
                            Do not repeat work that has already been completed.
                            Do not attempt unrelated parts of the original user request.
                            Return the result required for the current delegated task.
                            """)
                    .build();

            String prompt = promptTemplate.render(Map.of("message", chatRequest.originalMessage(),"task",chatRequest.task(),"previousResults",
                    chatRequest.context().getPreviousResults().stream().filter(Objects::nonNull).map(result -> """
                Specialist: %s
                Status: %s
                Result: %s
                Sources: %s
                """.formatted(
                                    result.specialistsType(),
                                    result.status(),
                                    result.result(),
                                    result.sources()
                            ))
                            .collect(Collectors.joining("\n\n"))));
            SpecialistResult response = client.getChatResponseWithToolCall(
                    KNOWLEDGE_SPECIALIST_SYSTEM_PROMPT,
                    prompt,
                    conversationId,
                    SpecialistResult.class,
                    knowledgeSearchTool,
                    plannerTool
            );
            validateSpecialistResult(response);
            log.info("service.agent.response.completed operation=get.knowledge.Search status=success");
            return response;
        } catch (AiDomainException | AiProviderException | ToolValidationException | UnsupportedDocumentTypeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("service.agent.response.failed operation=get.knowledge.Search status=error errorCode={} exception={}",
                    resolveErrorCode(e), e.getClass().getSimpleName());
            throw aiExceptionTranslator.translate(e);
        }
    }

    @Override
    public SpecialistsType getSpecialistType() {
        return SpecialistsType.KNOWLEDGE;
    }

    private void validateSpecialistResult(SpecialistResult response) {
        if (response == null) {
            throw new InvalidSpecialistResultException("Knowledge specialist returned a null result");
        }
        if (response.specialistsType() == null) {
            throw new InvalidSpecialistResultException("Knowledge specialist result is missing specialistsType");
        }
        if (response.status() == null) {
            throw new InvalidSpecialistResultException("Knowledge specialist result is missing status");
        }
        if (response.result() == null) {
            throw new InvalidSpecialistResultException("Knowledge specialist result is missing result content");
        }
    }

    private String resolveErrorCode(Throwable throwable) {
        if (throwable instanceof AiProviderException providerException) {
            return providerException.getErrorCode();
        }
        if (throwable.getCause() instanceof AiProviderException providerException) {
            return providerException.getErrorCode();
        }
        return "APPLICATION_ERROR";
    }
}
