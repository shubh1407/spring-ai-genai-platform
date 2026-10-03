package com.spring_ai.ai_copilot.agent.specialists;

import com.spring_ai.ai_copilot.agent.dto.SpecialistsType;
import com.spring_ai.ai_copilot.exception.SpecialistNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class SpecialistResolver {

    private final Map<String,Specialist> specialistsAgentsMap;

    public SpecialistResolver(Map<String,Specialist> specialistsAgentsMap) {
        this.specialistsAgentsMap = specialistsAgentsMap;
    }

    public Specialist getSpecialistAgent(SpecialistsType specialistsType) {
        Optional<Specialist> specialistAgent = specialistsAgentsMap.values().stream()
                .filter(specialist -> specialist.getSpecialistType().equals(specialistsType))
                .findAny();
        return specialistAgent.orElseThrow(() -> new SpecialistNotFoundException(specialistsType));
    }
}
