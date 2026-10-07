package com.spring_ai.ai_copilot.controller;

import com.spring_ai.ai_copilot.agent.dto.OrchestratorResponse;
import com.spring_ai.ai_copilot.agent.dto.SpecialistRequest;
import com.spring_ai.ai_copilot.agent.dto.SpecialistResult;
import com.spring_ai.ai_copilot.agent.dto.SupervisorDecision;
import com.spring_ai.ai_copilot.agent.services.AgentService;
import com.spring_ai.ai_copilot.agent.services.Orchestrator;
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

    private final Orchestrator orchestrator;

    public AgentController(AgentService agentService, TenantContext tenantContext, Orchestrator orchestrator) {
        this.agentService = agentService;
        this.tenantContext = tenantContext;
        this.orchestrator = orchestrator;
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
            AgentResponse response = agentService.getKnowledgeSearch(chatRequest);
            log.info("controller.agent.request.completed operation=agent.chat.request status=success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("controller.agent.request.failed operation=agent.chat.request status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/supervisor/route")
    public ResponseEntity<OrchestratorResponse> supervisorRoute(@RequestBody AgentChatRequest chatRequest) {
        tenantContext.setTenantId(chatRequest.tenantId());
        log.info("controller.agent.request.started operation=agent.chat.request");
        try {
            OrchestratorResponse response = orchestrator.orchestrate(chatRequest);
            log.info("controller.agent.request.completed operation=agent.chat.request status=success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("controller.agent.request.failed operation=agent.chat.request status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/test-mcp-resources")
    public ResponseEntity<String> testMcpResources() {
        return ResponseEntity.ok(agentService.testMcpResources());
    }

    @PostMapping("/test-mcp-prompt")
    public ResponseEntity<String> testMcpPrompt() {
        return ResponseEntity.ok(agentService.testMcpPrompts());
    }
}
