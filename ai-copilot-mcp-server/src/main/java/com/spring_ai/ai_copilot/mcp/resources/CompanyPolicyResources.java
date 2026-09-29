package com.spring_ai.ai_copilot.mcp.resources;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

@Component
public class CompanyPolicyResources {

    @McpResource(
            uri = "company://policies/{policyName}",
            name = "Company Policy",
            description = "Provides company policy content by policy name.",
            mimeType = "text/plain"
    )
    public String companyPolicy(@McpArg String policyName) {

        if (policyName == null || policyName.isBlank()) {
            throw new IllegalArgumentException("policyName cannot be null or blank");
        }

        if ("refund".equalsIgnoreCase(policyName.trim())) {
            return """
                    Company Refund Policy

                    STANDARD customers:
                    Refunds are allowed within 45 days of purchase.

                    ENTERPRISE customers:
                    Refunds are allowed within 60 days of purchase.
                    """;
        }
        throw new IllegalArgumentException("Unknown policy name: " + policyName);
    }
}
