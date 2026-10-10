package com.spring_ai.ai_copilot.approval.service;

import com.spring_ai.ai_copilot.agent.dto.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockProtectedActionService {

    private static final Logger log = LoggerFactory.getLogger(MockProtectedActionService.class);

    public String execute(WorkflowContext context) {
        if (context == null || context.getPendingProtectedAction() == null) {
            return "No protected action pending.";
        }

        String result = "Mock protected action SEND_REPORT completed for conversationId="
                + context.getConversationId() + ", workflowId=" + context.getWorkflowId();
        log.info("PROTECTED_ACTION_EXECUTED workflowId={} conversationId={} action={}",
                context.getWorkflowId(), context.getConversationId(), context.getPendingProtectedAction());
        return result;
    }
}
