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
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final KnowledgeSearchTool knowledgeSearchTool;

    private final PlannerTool plannerTool;

    private final SyncMcpToolCallbackProvider mcpTools;

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

        The execution cycle may therefore become:

        User Goal
             ↓
        Decide
             ↓
        Planning Required?
             ↓
            YES
             ↓
        createExecutionPlan
             ↓
        Execute Tools
             ↓
        Validate Results
             ↓
        ┌─── Result Sufficient? ───┐
        │                          │
       YES                        NO
        │                          │
        ↓                          ↓
      Finish              createExecutionPlan
                                   ↓
                              Revised Plan
                                   ↓
                              Execute Again
                                   ↓
                                Validate


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

    private final AiExceptionTranslator aiExceptionTranslator;
    private final AiProviderClient client;

    public AgentService(KnowledgeSearchTool knowledgeSearchTool, PlannerTool plannerTool, SyncMcpToolCallbackProvider mcpTools, AiExceptionTranslator aiExceptionTranslator, AiProviderClient client) {
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.plannerTool = plannerTool;
        this.mcpTools = mcpTools;
        this.aiExceptionTranslator = aiExceptionTranslator;
        this.client = client;
    }

    public String getChatResponse(AgentChatRequest chatRequest) {
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
            String response = client.getChatResponseWithToolCall(SYSTEM_PROMPT,prompt, chatRequest.conversationId(),knowledgeSearchTool,plannerTool,mcpTools);
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
