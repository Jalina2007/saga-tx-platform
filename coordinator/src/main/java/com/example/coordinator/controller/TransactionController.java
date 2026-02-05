package com.example.coordinator.controller;

import com.example.coordinator.dto.BeginTransactionResponse;
import com.example.coordinator.dto.RegisterStepRequest;
import com.example.coordinator.dto.StepResultRequest;
import com.example.coordinator.dto.StepStateResponse;
import com.example.coordinator.dto.TransactionStateResponse;
import com.example.coordinator.service.TransactionCoordinatorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tx")
public class TransactionController {

    private final TransactionCoordinatorService transactionCoordinatorService;

    public TransactionController(TransactionCoordinatorService transactionCoordinatorService) {
        this.transactionCoordinatorService = transactionCoordinatorService;
    }

    @PostMapping("/begin")
    public ResponseEntity<BeginTransactionResponse> beginTransaction() {
        return ResponseEntity.ok(transactionCoordinatorService.beginTransaction());
    }

    @PostMapping("/{xid}/steps")
    public ResponseEntity<StepStateResponse> registerStep(
            @PathVariable String xid,
            @Valid @RequestBody RegisterStepRequest request
    ) {
        return ResponseEntity.ok(transactionCoordinatorService.registerStep(xid, request));
    }

    @PostMapping("/{xid}/steps/{stepId}/success")
    public ResponseEntity<StepStateResponse> markStepSuccess(
            @PathVariable String xid,
            @PathVariable Long stepId,
            @RequestBody(required = false) StepResultRequest request
    ) {
        StepResultRequest safeRequest = request == null ? new StepResultRequest(null, null) : request;
        return ResponseEntity.ok(transactionCoordinatorService.markStepSuccess(xid, stepId, safeRequest));
    }

    @PostMapping("/{xid}/steps/{stepId}/failure")
    public ResponseEntity<StepStateResponse> markStepFailure(
            @PathVariable String xid,
            @PathVariable Long stepId,
            @RequestBody(required = false) StepResultRequest request
    ) {
        StepResultRequest safeRequest = request == null ? new StepResultRequest(null, null) : request;
        return ResponseEntity.ok(transactionCoordinatorService.markStepFailure(xid, stepId, safeRequest));
    }

    @GetMapping("/{xid}")
    public ResponseEntity<TransactionStateResponse> getTransactionState(@PathVariable String xid) {
        return ResponseEntity.ok(transactionCoordinatorService.getTransactionState(xid));
    }
}
