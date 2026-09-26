package com.spring_ai.ai_copilot.agent.tools;

import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.dto.AgentPlan;
import com.spring_ai.ai_copilot.exception.ToolValidationException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlannerTool {

    private final AiProviderClient aiProviderClient;

    public PlannerTool(AiProviderClient aiProviderClient) {
        this.aiProviderClient = aiProviderClient;
    }

    @Tool(
            name = "createExecutionPlan",
            description = """
            Create an execution plan for a complex user request that requires
            multiple steps or multiple tools. Use this tool when planning can
            help break the user's goal into meaningful actions.
            Do not use it for simple questions that can be answered directly.
            """
    )
    public AgentPlan createExecutionPlan(
            @ToolParam(
                    required = true,
                    description = "The user's goal or request that needs to be planned."
            )
            String goal
    ) {
        if (goal == null || goal.isBlank()) {
            throw new ToolValidationException(
                    "Planning goal must not be blank"
            );
        }

        if (goal.length() > 1000) {
            throw new ToolValidationException(
                    "Planning goal is too long"
            );
        }

        return aiProviderClient.createPlan(goal);
    }
}
