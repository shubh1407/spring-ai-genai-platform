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
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
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

    private final List<McpSyncClient> mcpClients;

    public AgentService(KnowledgeSearchTool knowledgeSearchTool, PlannerTool plannerTool, SyncMcpToolCallbackProvider mcpTools, AiExceptionTranslator aiExceptionTranslator, AiProviderClient client, List<McpSyncClient> mcpClients) {
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.plannerTool = plannerTool;
        this.mcpTools = mcpTools;
        this.aiExceptionTranslator = aiExceptionTranslator;
        this.client = client;
        this.mcpClients = mcpClients;
    }

    public AgentResponse getKnowledgeSearch(AgentChatRequest chatRequest) {
        String operation = "agent.response";
        long startedAt = System.nanoTime();

        log.info(
                "agent.request.started operation={} conversationId={} messageLength={}",
                operation,
                chatRequest.conversationId(),
                chatRequest.message() == null
                        ? 0
                        : chatRequest.message().length()
        );

        try {
            PromptTemplate promptTemplate = PromptTemplate.builder()
                    .template("""
                            You are a helpful AI assistant.
                            Answer the user's question clearly and concisely.

                            User message:
                            {message}
                            
                            tenantId:
                            {tenantId}
                            """)
                    .build();

            String prompt = promptTemplate.render(Map.of("message", chatRequest.message(),"tenantId",chatRequest.tenantId()));
            AgentResponse response = client.getChatResponseWithToolCall(AGENT_SYSTEM_PROMPT,prompt, chatRequest.conversationId(),AgentResponse.class,knowledgeSearchTool,plannerTool);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.info(
                    "agent.request.completed operation={} conversationId={} latencyMs={} status=success",
                    operation,
                    chatRequest.conversationId(),
                    latencyMs
            );

            return response;
        } catch (Exception e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.warn(
                    "agent.request.failed operation={} conversationId={} latencyMs={} errorCode={} exception={}",
                    operation,
                    chatRequest.conversationId(),
                    latencyMs,
                    resolveErrorCode(e),
                    e.getClass().getSimpleName()
            );
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
        String operation = "agent.response.mcp";
        long startedAt = System.nanoTime();

        log.info(
                "agent.request.started operation={} conversationId={} messageLength={}",
                operation,
                chatRequest.conversationId(),
                chatRequest.message() == null
                        ? 0
                        : chatRequest.message().length()
        );

        try {
            PromptTemplate promptTemplate = PromptTemplate.builder()
                    .template("""
                            You are a helpful AI assistant.
                            Answer the user's question clearly and concisely.

                            User message:
                            {message}
                            """)
                    .build();

            String prompt = promptTemplate.render(
                    Map.of("message", chatRequest.message())
            );

            // MCP Resource
            String policy = testMcpResources();

            // MCP Prompt
            String mcpPrompt = testMcpPrompts();

            String enrichedPrompt = """
                    MCP WORKFLOW INSTRUCTIONS:
                    %s

                    MCP POLICY CONTEXT:
                    %s

                    USER REQUEST:
                    %s
                    """.formatted(
                    mcpPrompt,
                    policy,
                    prompt
            );

            log.info(
                    "agent.mcp.context.prepared operation={} conversationId={} policyLength={} promptLength={}",
                    operation,
                    chatRequest.conversationId(),
                    policy.length(),
                    mcpPrompt.length()
            );

            /*
             * MCP tools are passed as normal tool callbacks.
             */
            AgentResponse response =
                    client.getChatResponseWithToolCall(
                            AGENT_SYSTEM_PROMPT,
                            enrichedPrompt,
                            chatRequest.conversationId(),
                            AgentResponse.class,
                            knowledgeSearchTool,
                            plannerTool,
                            mcpTools
                    );

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.info(
                    "agent.request.completed operation={} conversationId={} latencyMs={} status=success",
                    operation,
                    chatRequest.conversationId(),
                    latencyMs
            );

            return response.message();

        } catch (Exception e) {

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.warn(
                    "agent.request.failed operation={} conversationId={} latencyMs={} errorCode={} exception={}",
                    operation,
                    chatRequest.conversationId(),
                    latencyMs,
                    resolveErrorCode(e),
                    e.getClass().getSimpleName()
            );

            throw aiExceptionTranslator.translate(e);
        }
    }


    public String testMcpResources() {

        String uri = "company://policies/refund";
        long startedAt = System.nanoTime();

        log.info(
                "mcp.resource.request.started uri={}",
                uri
        );

        try {

            McpSyncClient client = mcpClients.getFirst();

            McpSchema.ReadResourceRequest request =
                    McpSchema.ReadResourceRequest.builder(uri)
                            .build();

            McpSchema.ReadResourceResult result =
                    client.readResource(request);

            String content =
                    ((McpSchema.TextResourceContents)
                            result.contents().getFirst())
                            .text();

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.info(
                    "mcp.resource.request.completed uri={} latencyMs={} contentLength={} status=success",
                    uri,
                    latencyMs,
                    content.length()
            );

            return content;

        } catch (Exception e) {

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.warn(
                    "mcp.resource.request.failed uri={} latencyMs={} exception={}",
                    uri,
                    latencyMs,
                    e.getClass().getSimpleName()
            );

            throw e;
        }
    }

    public String testMcpPrompts() {

        String promptName = "refund-analysis";
        long startedAt = System.nanoTime();

        log.info(
                "mcp.prompt.request.started prompt={}",
                promptName
        );

        try {

            McpSyncClient client = mcpClients.getFirst();

            McpSchema.GetPromptRequest request =
                    McpSchema.GetPromptRequest
                            .builder(promptName)
                            .arguments(
                                    Map.of(
                                            "customerType", "STANDARD",
                                            "purchaseDate", "2026-09-01"
                                    )
                            )
                            .meta(null)
                            .build();

            McpSchema.GetPromptResult result =
                    client.getPrompt(request);

            String content =
                    ((McpSchema.TextContent)
                            result.messages()
                                    .getFirst()
                                    .content())
                            .text();

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.info(
                    "mcp.prompt.request.completed prompt={} latencyMs={} contentLength={} status=success",
                    promptName,
                    latencyMs,
                    content.length()
            );

            return content;

        } catch (Exception e) {

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.warn(
                    "mcp.prompt.request.failed prompt={} latencyMs={} exception={}",
                    promptName,
                    latencyMs,
                    e.getClass().getSimpleName()
            );

            throw e;
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
