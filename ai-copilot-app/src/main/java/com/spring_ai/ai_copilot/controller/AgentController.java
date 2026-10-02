package com.spring_ai.ai_copilot.controller;

import com.spring_ai.ai_copilot.agent.dto.SupervisorDecision;
import com.spring_ai.ai_copilot.agent.services.AgentService;
import com.spring_ai.ai_copilot.agent.tools.TenantContext;
import com.spring_ai.ai_copilot.dto.AgentChatRequest;
import com.spring_ai.ai_copilot.dto.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;

    private final TenantContext tenantContext;

    public AgentController(AgentService agentService, TenantContext tenantContext) {
        this.agentService = agentService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/agent-ask-mcp")
    public ResponseEntity<String> agentAskWithMcpTool(@RequestBody AgentChatRequest chatRequest) {
        tenantContext.setTenantId(chatRequest.tenantId());
        log.info("controller.agent.request.started operation=agent.chat.request");
        try {
            String response = agentService.getChatResponseWithMcpTools(chatRequest);
            log.info("controller.agent.request.completed operation=agent.chat.request status=success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("controller.agent.request.failed operation=agent.chat.request status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/agent-ask")
    public ResponseEntity<AgentResponse> agentAsk(@RequestBody AgentChatRequest chatRequest) {
        tenantContext.setTenantId(chatRequest.tenantId());
        log.info("controller.agent.request.started operation=agent.chat.request");
        try {
            AgentResponse response = agentService.getChatResponse(chatRequest);
            log.info("controller.agent.request.completed operation=agent.chat.request status=success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("controller.agent.request.failed operation=agent.chat.request status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/supervisor/route")
    public ResponseEntity<SupervisorDecision> supervisorRoute(@RequestBody AgentChatRequest chatRequest) {
        tenantContext.setTenantId(chatRequest.tenantId());
        log.info("controller.agent.request.started operation=agent.chat.request");
        try {
            SupervisorDecision response = agentService.getSupervisorResponse(chatRequest);
            log.info("controller.agent.request.completed operation=agent.chat.request status=success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("controller.agent.request.failed operation=agent.chat.request status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
