package com.spring_ai.ai_copilot.dto;

public record RefundEligibilityResponse(
        boolean eligible,
        String customerType,
        String purchaseDate,
        int refundWindowDays,
        long daysSincePurchase,
        String reason
) {}
