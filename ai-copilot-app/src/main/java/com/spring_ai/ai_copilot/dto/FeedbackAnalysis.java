package com.spring_ai.ai_copilot.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record FeedbackAnalysis(

        String sentiment,
        @DecimalMax(value = "1.0")
        @DecimalMin(value = "0")
        Double score,
        String summary
){ }