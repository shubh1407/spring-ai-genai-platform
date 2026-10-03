package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.WorkflowContext;
import com.spring_ai.ai_copilot.agent.dto.SupervisorDecision;
import com.spring_ai.ai_copilot.agent.tools.KnowledgeSearchTool;
import com.spring_ai.ai_copilot.agent.tools.PlannerTool;
import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import com.spring_ai.ai_copilot.dto.AgentResponse;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import com.spring_ai.ai_copilot.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.spring_ai.ai_copilot.constant.PromptsConstants.AGENT_SYSTEM_PROMPT;
import static com.spring_ai.ai_copilot.constant.PromptsConstants.SUPERVISOR_SYSTEM_PROMPT;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final KnowledgeSearchTool knowledgeSearchTool;

    private final PlannerTool plannerTool;

    private final SyncMcpToolCallbackProvider mcpTools;

    private final AiExceptionTranslator aiExceptionTranslator;

    private final AiProviderClient client;

    private final SpecialistService specialistService;

    public AgentService(KnowledgeSearchTool knowledgeSearchTool, PlannerTool plannerTool, SyncMcpToolCallbackProvider mcpTools, AiExceptionTranslator aiExceptionTranslator, AiProviderClient client, SpecialistService specialistService) {
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.plannerTool = plannerTool;
        this.mcpTools = mcpTools;
        this.aiExceptionTranslator = aiExceptionTranslator;
        this.client = client;
        this.specialistService = specialistService;
    }

    public AgentResponse getKnowledgeSearch(AgentChatRequest chatRequest) {
        log.info("service.agent.response.started operation=get.knowledge.Search");
        try {
            PromptTemplate promptTemplate = PromptTemplate.builder()
                    .template("""
                            You are a helpful AI assistant.
                            Answer the user's question clearly and concisely.

                            User message:
                            {message}
                            """)
                    .build();

            String prompt = promptTemplate.render(Map.of("message", chatRequest.message()));
            AgentResponse response = client.getChatResponseWithToolCall(AGENT_SYSTEM_PROMPT,prompt, chatRequest.conversationId(),AgentResponse.class,knowledgeSearchTool,plannerTool);
            log.info("service.agent.response.completed operation=get.knowledge.Search status=success");
            return response;
        } catch (Exception e) {
            log.warn("service.agent.response.failed operation=get.knowledge.Search status=error errorCode={} exception={}",
                    resolveErrorCode(e), e.getClass().getSimpleName());
            throw aiExceptionTranslator.translate(e);
        }
    }


    public SupervisorDecision getSupervisorResponse(WorkflowContext chatRequest) {
        log.info("service.agent.response.started operation=get.Supervisor.response");
        try {
            PromptTemplate promptTemplate = PromptTemplate.builder()
                    .template("""
                            You are a helpful AI assistant.
                            Answer the user's question clearly and concisely.

                            User message:
                            {message}
                            Previous Specialist Results:
                            {previousResults}
                            """)
                    .build();

            String prompt = promptTemplate.render(Map.of("message", chatRequest.getOriginalRequest()
                    ,"previousResults",
                    chatRequest.getPreviousResults().stream().filter(Objects::nonNull).map(result -> """
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
            SupervisorDecision response = client.getChatResponseWithToolCall(SUPERVISOR_SYSTEM_PROMPT,prompt,chatRequest.getConversationId() , SupervisorDecision.class);
            log.info("service.agent.response.completed operation=get.Supervisor.response status=success");
            return response;
            //return specialistService.getSpecialist(response,chatRequest.conversationId());
        } catch (Exception e) {
            log.warn("service.agent.response.failed operation=get.Supervisor.response status=error errorCode={} exception={}",
                    resolveErrorCode(e), e.getClass().getSimpleName());
            throw aiExceptionTranslator.translate(e);
        }
    }

    public String getChatResponseWithMcpTools(AgentChatRequest chatRequest) {
        log.info("service.agent.response.started operation=chat.response.with.Mcp.tools");
        try {
            PromptTemplate promptTemplate = PromptTemplate.builder()
                    .template("""
                            You are a helpful AI assistant.
                            Answer the user's question clearly and concisely.

                            User message:
                            {message}
                            """)
                    .build();

            String prompt = promptTemplate.render(Map.of("message", chatRequest.message()));
            String response = client.getChatResponseWithToolCallWithMcpTools(AGENT_SYSTEM_PROMPT,prompt, chatRequest.conversationId(),knowledgeSearchTool,plannerTool,mcpTools);
            log.info("service.agent.response.completed operation=chat.response.with.Mcp.tools status=success");
            return response;
        } catch (Exception e) {
            log.warn("service.agent.response.failed operation=chat.response.with.Mcp.tools status=error errorCode={} exception={}",
                    resolveErrorCode(e), e.getClass().getSimpleName());
            throw aiExceptionTranslator.translate(e);
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
