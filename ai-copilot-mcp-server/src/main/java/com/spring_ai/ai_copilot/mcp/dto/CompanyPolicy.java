package com.spring_ai.ai_copilot.mcp.dto;

public record CompanyPolicy(
        String policyName,
        int refundWindowDays,
        String[] applicableCustomerTypes
) {
}
