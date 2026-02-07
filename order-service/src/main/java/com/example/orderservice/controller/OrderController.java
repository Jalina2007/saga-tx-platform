package com.example.orderservice.controller;

import com.example.orderservice.client.CoordinatorClient;
import com.example.orderservice.dto.RegisterStepRequest;
import com.example.orderservice.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;
    private final CoordinatorClient coordinatorClient;

    public OrderController(
            OrderService orderService,
            CoordinatorClient coordinatorClient
    ) {
        this.orderService = orderService;
        this.coordinatorClient = coordinatorClient;
    }

    @PostMapping
    public ResponseEntity<String> createOrder(
            @RequestHeader("X-XID") String xid,
            @RequestBody Map<String, String> body
    ) {
        String orderRef = requireOrderRef(body);
        Long stepId = coordinatorClient.registerStep(xid, buildStep(orderRef));

        try {
            orderService.createOrder(orderRef);
            coordinatorClient.markSuccess(xid, stepId);
        } catch (RuntimeException exception) {
            coordinatorClient.markFailure(xid, stepId, exception.getMessage());
            throw exception;
        }

        return ResponseEntity.ok("Order created");
    }

    @PostMapping("/compensate/cancel")
    public ResponseEntity<String> cancelOrder(@RequestBody Map<String, String> body) {
        orderService.cancelOrder(requireOrderRef(body));
        return ResponseEntity.ok("Order cancelled");
    }

    private RegisterStepRequest buildStep(String orderRef) {
        RegisterStepRequest step = new RegisterStepRequest();
        String payload = toPayload(orderRef);

        step.setServiceName("order-service");
        step.setActionName("/orders");
        step.setCompensationName("/orders/compensate/cancel");
        step.setStepOrder(1);
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
