package com.example.coordinator.dto;

public record StepStateResponse(
        Long stepId,
        String serviceName,
        String actionName,
        String compensationName,
        Integer stepOrder,
        String status
) {
}
