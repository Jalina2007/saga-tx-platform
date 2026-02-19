package com.example.inventoryservice.controller;

import com.example.inventoryservice.client.CoordinatorClient;
import com.example.inventoryservice.dto.RegisterStepRequest;
import com.example.inventoryservice.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;
    private final CoordinatorClient coordinatorClient;

    public InventoryController(
            InventoryService inventoryService,
            CoordinatorClient coordinatorClient
    ) {
        this.inventoryService = inventoryService;
        this.coordinatorClient = coordinatorClient;
    }

    @PostMapping("/reserve")
    public ResponseEntity<String> reserve(
            @RequestHeader("X-XID") String xid,
            @RequestBody Map<String, String> body
    ) {
        String orderRef = requireOrderRef(body);
        log.info("XID: {} - Processing inventory for {}", xid, orderRef);

        Long stepId = coordinatorClient.registerStep(xid, buildStep(orderRef));

        try {
            inventoryService.reserve(orderRef);
            coordinatorClient.markSuccess(xid, stepId);
        } catch (RuntimeException exception) {
            coordinatorClient.markFailure(xid, stepId, exception.getMessage());
            throw exception;
        }

        return ResponseEntity.ok("Reserved");
    }

    @PostMapping("/compensate/release")
    public ResponseEntity<String> release(@RequestBody Map<String, String> body) {
        inventoryService.release(requireOrderRef(body));
        return ResponseEntity.ok("Released");
    }

    private RegisterStepRequest buildStep(String orderRef) {
        RegisterStepRequest step = new RegisterStepRequest();
        String payload = toPayload(orderRef);

        step.setServiceName("inventory-service");
        step.setActionName("/inventory/reserve");
        step.setCompensationName("/inventory/compensate/release");
        step.setStepOrder(3);
        step.setRequestPayload(payload);
        step.setCompensationPayload(payload);

        return step;
    }

    private String requireOrderRef(Map<String, String> body) {
        String orderRef = body.get("orderRef");

        if (orderRef == null || orderRef.isBlank()) {
            throw new IllegalArgumentException("orderRef is required");
        }

        return orderRef;
    }

    private String toPayload(String orderRef) {
        return "{\"orderRef\":\"" + escapeJson(orderRef) + "\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
