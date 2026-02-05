package com.example.coordinator.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TransactionStateResponse(
        String xid,
        String status,
        String failureReason,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<StepStateResponse> steps
) {
}
