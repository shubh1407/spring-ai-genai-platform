package com.spring_ai.ai_copilot.mcp.tools;

import com.spring_ai.ai_copilot.mcp.tools.dto.CompanyPolicy;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CompanyPolicyTools {

    @McpTool(
            name = "getCompanyPolicy",
            description = "Retrieve a company policy by policy name."
    )
    public CompanyPolicy getCompanyPolicy(@McpToolParam(
            description = "The policy name, for example refund",
            required = true
    )
    String policyName){

        if (policyName == null || policyName.isBlank()) {
            throw new IllegalArgumentException(
                    "Policy name must not be blank"
            );
        }

        if ("refund".equalsIgnoreCase(policyName.trim())) {
            return new CompanyPolicy(
                    "Company Refund Policy",
                    45,
                    new String[]{"STANDARD", "ENTERPRISE"}
            );
        }
        throw new IllegalArgumentException(
                "Unknown policy: " + policyName
        );
    }
}
