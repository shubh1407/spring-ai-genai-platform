package com.spring_ai.ai_copilot.agent.dto;

public record OrchestratorResponse(
        WorkflowStatus status,
        String questionToUser,
        SpecialistResult result
) {

    public static OrchestratorResponse waitingForUser(
            String questionToUser) {

        return new OrchestratorResponse(
                WorkflowStatus.WAITING_FOR_USER_INPUT,
                questionToUser,
                null
        );
    }

    public static OrchestratorResponse completed(
            SpecialistResult result) {

        return new OrchestratorResponse(
                WorkflowStatus.COMPLETED,
                null,
                result
        );
    }

    public static OrchestratorResponse failed(
            String message) {

        return new OrchestratorResponse(
                WorkflowStatus.FAILED,
                message,
                null
        );
    }
}