package com.example.coordinator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterStepRequest(
        @NotBlank String serviceName,
        @NotBlank String actionName,
        @NotBlank String compensationName,
        @NotNull Integer stepOrder,
        String requestPayload,
        String compensationPayload
) {
}
