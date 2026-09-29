package com.spring_ai.ai_copilot.agent.tools;

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
import java.util.concurrent.TimeUnit;

@Service
public class AgentService {

    private static final Logger log =
            LoggerFactory.getLogger(AgentService.class);

    private final KnowledgeSearchTool knowledgeSearchTool;
    private final PlannerTool plannerTool;
    private final SyncMcpToolCallbackProvider mcpTools;
    private final List<McpSyncClient> mcpClients;
    private final AiExceptionTranslator aiExceptionTranslator;
    private final AiProviderClient client;

    private static final String SYSTEM_PROMPT = """
        You are an enterprise AI agent responsible for solving the user's request
        accurately, safely, and completely.

        Your execution process must follow this lifecycle:

        UNDERSTAND → DECIDE → PLAN → EXECUTE → VALIDATE → REPLAN IF NEEDED → COMPLETE

        1. UNDERSTAND THE USER'S GOAL

        First understand the user's complete goal, required outcome, constraints,
        and information available from the conversation.

        Do not start executing tools until you understand what the user is asking.

        2. DECIDE WHETHER PLANNING IS REQUIRED

        Decide whether the task requires planning.

        For simple requests that can be reliably answered directly:
        - do not create a plan
        - answer directly

        For complex requests involving multiple actions, dependent steps,
        information gathering, decisions, or business actions:
        - planning is required
        - call the createExecutionPlan tool before executing business tools

        Do not skip the planning step for a complex task.

        3. CREATE THE EXECUTION PLAN

        When planning is required, call the createExecutionPlan tool.

        The planning tool creates a structured plan for the user's goal.

        The plan is guidance for execution.
        It is NOT a deterministic workflow and must NOT be blindly followed.

        After receiving the plan:
        - inspect the plan
        - understand the dependencies between steps
        - determine which available tools are required
        - execute the appropriate tools according to the plan

        4. EXECUTE THE PLAN

        Execute the required business tools using the plan and the user's goal
        as context.

        The plan does not determine which tool calls must happen mechanically.
        You remain responsible for deciding the next action.

        After every tool result:
        - inspect the result
        - use the result as new context
        - determine whether another action is required
        - do not assume that a successful tool call means the user's goal
          has been completed

        5. VALIDATE THE RESULT

        Before producing the final answer, validate whether the information
        obtained from the executed tools is sufficient to reliably satisfy
        the user's original goal.

        Ask yourself:

        - Did the tools provide the required information?
        - Are the required business decisions supported by actual tool results?
        - Is any required information missing?
        - Did the execution produce an unexpected or insufficient result?
        - Does the current result actually satisfy the user's goal?

        6. REPLAN WHEN NECESSARY

        If the result is insufficient, unexpected, incomplete, or indicates
        that the current approach cannot reliably complete the user's goal:

        - do NOT blindly continue with the existing plan
        - do NOT repeat the same failed action without a reason
        - call the createExecutionPlan tool again
        - provide the original goal together with the relevant information
          obtained during execution
        - use the new plan to determine the next actions

        Replanning is allowed whenever new information changes what needs
        to happen next.

        7. CONTINUE EXECUTION AFTER REPLANNING

        After receiving a revised plan:

        - inspect the revised plan
        - select the appropriate available tools
        - execute the required actions
        - validate the new results again

        8. TOOL USAGE RULES

        Use tools based on their descriptions, input requirements,
        the user's goal, the current plan, and the results of previous tools.

        When a tool is required:
        - actually invoke the tool
        - do not merely mention or describe the tool
        - provide valid inputs
        - use information obtained from previous tool results when applicable

        Never simulate tool execution or invent tool results.

        9. KNOWLEDGE AND ACCURACY

        Do not invent:
        - company policies
        - internal knowledge
        - customer information
        - business decisions
        - tool results
        - execution results

        When company-specific information is required, use the appropriate
        available capability.

        If reliable information cannot be obtained:
        - identify what information is missing
        - ask the user for it when appropriate
        - otherwise clearly state that the information is unavailable

        10. FINAL RESPONSE

        Only provide the final answer after validating that the available
        information is sufficient to address the user's original goal.

        The final response must:
        - be concise
        - be based on actual execution results
        - not expose hidden chain-of-thought or private reasoning
        - not claim actions were completed when they were not completed
        """;

    public AgentService(
            KnowledgeSearchTool knowledgeSearchTool,
            PlannerTool plannerTool,
            SyncMcpToolCallbackProvider mcpTools,
            List<McpSyncClient> mcpClients,
            AiExceptionTranslator aiExceptionTranslator,
            AiProviderClient client) {

        this.knowledgeSearchTool = knowledgeSearchTool;
        this.plannerTool = plannerTool;
        this.mcpTools = mcpTools;
        this.mcpClients = mcpClients;
        this.aiExceptionTranslator = aiExceptionTranslator;
        this.client = client;
    }

    public AgentResponse getChatResponse(AgentChatRequest chatRequest) {

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
                            """)
                    .build();

            String prompt = promptTemplate.render(
                    Map.of("message", chatRequest.message())
            );

            AgentResponse response =
                    client.getChatResponseWithToolCall(
                            SYSTEM_PROMPT,
                            prompt,
                            chatRequest.conversationId(),
                            knowledgeSearchTool,
                            plannerTool
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

    public String getChatResponseWithMcpTools(
            AgentChatRequest chatRequest) {

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
                            SYSTEM_PROMPT,
                            enrichedPrompt,
                            chatRequest.conversationId(),
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

    private String resolveErrorCode(Throwable throwable) {

        if (throwable instanceof AiProviderException providerException) {
            return providerException.getErrorCode();
        }

        if (throwable.getCause()
                instanceof AiProviderException providerException) {

            return providerException.getErrorCode();
        }

        return "APPLICATION_ERROR";
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
}