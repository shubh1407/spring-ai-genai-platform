package com.spring_ai.ai_copilot.mcp.prompt;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompanyPolicyPrompts {

    @McpPrompt(name = "refund-analysis",
            description = "Create a prompt for analyzing refund eligibility.")
    public McpSchema.GetPromptResult refundAnalysis(
            @McpArg(
                name = "customerType",
                description = "Customer type",
                required = true
            )
        String customerType,
        @McpArg(
                name = "purchaseDate",
                description = "Purchase date in yyyy-MM-dd format",
                required = true
        )
        String purchaseDate){

        String message = """
                Analyze refund eligibility for a customer.

                Customer type: %s
                Purchase date: %s

                Use the company refund policy.
                Explain the applicable refund window
                and whether the customer is eligible.
                """.formatted(customerType, purchaseDate);

        return McpSchema.GetPromptResult.builder(
                List.of(new McpSchema.PromptMessage(
                        McpSchema.Role.USER,
                        McpSchema.TextContent.builder(message).build()
                )))
                .description("Refund eligibility analysis prompt")
                .build();
    }
}
