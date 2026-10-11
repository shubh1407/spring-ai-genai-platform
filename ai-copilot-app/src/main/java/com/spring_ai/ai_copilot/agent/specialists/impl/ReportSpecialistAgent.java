package com.spring_ai.ai_copilot.agent.specialists.impl;

import com.spring_ai.ai_copilot.agent.dto.SpecialistRequest;
import com.spring_ai.ai_copilot.agent.dto.SpecialistResult;
import com.spring_ai.ai_copilot.agent.dto.SpecialistsType;
import com.spring_ai.ai_copilot.agent.services.AgentService;
import com.spring_ai.ai_copilot.agent.specialists.Specialist;
import com.spring_ai.ai_copilot.agent.tools.ReportGenerateTools;
import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import com.spring_ai.ai_copilot.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.spring_ai.ai_copilot.constant.PromptsConstants.REPORT_SYSTEM_PROMPT;

@Service
public class ReportSpecialistAgent implements Specialist {

    private static final Logger log = LoggerFactory.getLogger(ReportSpecialistAgent.class);

    private final AiProviderClient aiProviderClient;

    private final AiExceptionTranslator aiExceptionTranslator;

    private final ReportGenerateTools reportGenerateTools;

    public ReportSpecialistAgent(AiProviderClient aiProviderClient, AiExceptionTranslator aiExceptionTranslator, ReportGenerateTools reportGenerateTools) {
        this.aiProviderClient = aiProviderClient;
        this.aiExceptionTranslator = aiExceptionTranslator;
        this.reportGenerateTools = reportGenerateTools;
    }

    @Override
    public SpecialistResult execute(SpecialistRequest chatRequest,String conversationId) {
        try {
            log.info("Report Specialists agent is called for conversation id {},task {}, tenantId {}", chatRequest.conversationId(), chatRequest.task(), chatRequest.tenantId());
            String previousResult = chatRequest.context().getPreviousResults().stream().filter(Objects::nonNull).map(result -> """
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
                    .collect(Collectors.joining("\n\n"));
            PromptTemplate promptTemplate = PromptTemplate.builder()
                    .template("""
                            Original User Request:
                            {message}
                            
                            Current Delegated Task:
                            {task}
                            
                            Previous Specialist Results:
                            {previousResults}
                            """)
                    .build();
            String prompt = promptTemplate.render(Map.of("message", chatRequest.originalMessage(), "task", chatRequest.task(),"previousResults",previousResult));
            return aiProviderClient.getChatResponseWithToolCall(REPORT_SYSTEM_PROMPT,prompt, conversationId, SpecialistResult.class,reportGenerateTools);
        }catch (AiDomainException | AiProviderException | ToolValidationException | UnsupportedDocumentTypeException e) {
            return new SpecialistResult(
                    SpecialistsType.REPORT,
                    "FAILED",
                    e.getMessage(),
                    null
            );
        } catch (Exception e) {
            log.warn("service.agent.response.failed operation=get.knowledge.Search status=error errorCode={} exception={}",
                    resolveErrorCode(e), e.getClass().getSimpleName());
            AiProviderException exception= aiExceptionTranslator.translate(e);
            return new SpecialistResult(
                    SpecialistsType.REPORT,
                    "FAILED",
                    exception.getMessage(),
                    null
            );
        }
    }

    @Override
    public SpecialistsType getSpecialistType() {
        return SpecialistsType.REPORT;
    }

    private void validateSpecialistResult(SpecialistResult response) {
        if (response == null) {
            throw new InvalidSpecialistResultException("Report specialist returned a null result");
        }
        if (response.specialistsType() == null) {
            throw new InvalidSpecialistResultException("Report specialist result is missing specialistsType");
        }
        if (response.status() == null) {
            throw new InvalidSpecialistResultException("Report specialist result is missing status");
        }
        if (response.result() == null) {
            throw new InvalidSpecialistResultException("Report specialist result is missing result content");
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
