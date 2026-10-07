package com.spring_ai.ai_copilot.agent.specialists;

import com.spring_ai.ai_copilot.agent.dto.SpecialistRequest;
import com.spring_ai.ai_copilot.agent.dto.SpecialistResult;
import com.spring_ai.ai_copilot.agent.dto.SpecialistsType;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;

public interface Specialist {

    SpecialistResult execute(SpecialistRequest agentChatRequest,String conversationId);

    SpecialistsType getSpecialistType();
}
