package com.spring_ai.ai_copilot.agent.specialists.impl;

import com.spring_ai.ai_copilot.agent.dto.SpecialistRequest;
import com.spring_ai.ai_copilot.agent.dto.SpecialistResult;
import com.spring_ai.ai_copilot.agent.dto.SpecialistsType;
import com.spring_ai.ai_copilot.agent.services.AgentService;
import com.spring_ai.ai_copilot.agent.specialists.Specialist;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class DataSpecialistAgent implements Specialist {

    private static final Logger log = LoggerFactory.getLogger(DataSpecialistAgent.class);

    @Override
    public SpecialistResult execute(SpecialistRequest chatRequest,String conversationId) {
        log.info("Data Specialists agent is called for conversation id {},task {}, tenantId {}",chatRequest.conversationId(),chatRequest.originalMessage(),chatRequest.tenantId());
        return new SpecialistResult(SpecialistsType.DATA,"success","26-Aug-2026",null);
    }

    @Override
    public SpecialistsType getSpecialistType() {
        return SpecialistsType.DATA;
    }
}
