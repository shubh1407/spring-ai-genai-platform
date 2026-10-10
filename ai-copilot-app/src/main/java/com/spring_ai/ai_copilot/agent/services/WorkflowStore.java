package com.spring_ai.ai_copilot.agent.services;

import com.spring_ai.ai_copilot.agent.dto.WorkflowContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class WorkflowStore {

    private final ConcurrentMap<String, WorkflowContext> workflows = new ConcurrentHashMap<>();

    public WorkflowContext save(WorkflowContext workflowContext) {
        workflows.put(workflowContext.getWorkflowId(), workflowContext);
        return workflowContext;
    }

    public Optional<WorkflowContext> findByWorkflowId(String workflowId) {
        return Optional.ofNullable(workflows.get(workflowId));
    }

    public List<WorkflowContext> findAll() {
        return List.copyOf(workflows.values());
    }
}
