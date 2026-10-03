package com.spring_ai.ai_copilot.agent.dto;

import com.spring_ai.ai_copilot.dto.Citation;

import java.util.List;

public record SpecialistResult(SpecialistsType specialistsType, String status, String result, List<Citation> sources) {
}
