package com.spring_ai.ai_copilot.mcp.tools.dto;

public record CompanyPolicy(
        String policyName,
        int refundWindowDays,
        String[] applicableCustomerTypes
) {
}
