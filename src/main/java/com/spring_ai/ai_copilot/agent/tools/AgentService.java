package com.spring_ai.ai_copilot.agent.tools;

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
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final KnowledgeSearchTool knowledgeSearchTool;

    private static final String SYSTEM_PROMPT="""
                You are an enterprise AI assistant.
                Help the user answer their questions accurately.
                Use the internal knowledge search tool when the question
                requires company-specific information.
                Do not invent company policies or internal facts.
                If the available knowledge is insufficient, clearly say
                that the information is not available.
                Use the tool only when it is relevant.
           """;

    private final AiExceptionTranslator aiExceptionTranslator;
    private final AiProviderClient client;

    public AgentService(KnowledgeSearchTool knowledgeSearchTool, AiExceptionTranslator aiExceptionTranslator, AiProviderClient client) {
        this.knowledgeSearchTool = knowledgeSearchTool;
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
            AgentResponse response = client.getChatResponseWithToolCall(SYSTEM_PROMPT,prompt, chatRequest.conversationId(),knowledgeSearchTool);
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
