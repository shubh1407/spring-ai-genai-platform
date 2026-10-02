package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.SupervisorDecision;
import com.spring_ai.ai_copilot.agent.tools.KnowledgeSearchTool;
import com.spring_ai.ai_copilot.agent.tools.PlannerTool;
import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import com.spring_ai.ai_copilot.dto.AgentResponse;
import com.spring_ai.ai_copilot.dto.ChatRequest;
import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import com.spring_ai.ai_copilot.exception.AiProviderException;
import com.spring_ai.ai_copilot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    public AgentService(KnowledgeSearchTool knowledgeSearchTool, PlannerTool plannerTool, SyncMcpToolCallbackProvider mcpTools, AiExceptionTranslator aiExceptionTranslator, AiProviderClient client) {
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.plannerTool = plannerTool;
        this.mcpTools = mcpTools;
        this.aiExceptionTranslator = aiExceptionTranslator;
        this.client = client;
    }

    public AgentResponse getChatResponse(AgentChatRequest chatRequest) {
        log.info("service.agent.response.started operation=chat.response");
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
            log.info("service.agent.response.completed operation=chat.response status=success");
            return response;
        } catch (Exception e) {
            log.warn("service.agent.response.failed operation=chat.response status=error errorCode={} exception={}",
                    resolveErrorCode(e), e.getClass().getSimpleName());
            throw aiExceptionTranslator.translate(e);
        }
    }


    public SupervisorDecision getSupervisorResponse(AgentChatRequest chatRequest) {
        log.info("service.agent.response.started operation=chat.response");
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
            SupervisorDecision response = client.getChatResponseWithToolCall(SUPERVISOR_SYSTEM_PROMPT,prompt, chatRequest.conversationId(), SupervisorDecision.class);
            log.info("service.agent.response.completed operation=chat.response status=success");
            return response;
        } catch (Exception e) {
            log.warn("service.agent.response.failed operation=chat.response status=error errorCode={} exception={}",
                    resolveErrorCode(e), e.getClass().getSimpleName());
            throw aiExceptionTranslator.translate(e);
        }
    }

    public String getChatResponseWithMcpTools(AgentChatRequest chatRequest) {
        log.info("service.agent.response.started operation=chat.response");
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
            log.info("service.agent.response.completed operation=chat.response status=success");
            return response;
        } catch (Exception e) {
            log.warn("service.agent.response.failed operation=chat.response status=error errorCode={} exception={}",
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
