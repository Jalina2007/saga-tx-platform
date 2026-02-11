package com.example.paymentservice.controller;

import com.example.paymentservice.client.CoordinatorClient;
import com.example.paymentservice.dto.RegisterStepRequest;
import com.example.paymentservice.service.PaymentService;
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
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final CoordinatorClient coordinatorClient;

    public PaymentController(
            PaymentService paymentService,
            CoordinatorClient coordinatorClient
    ) {
        this.paymentService = paymentService;
        this.coordinatorClient = coordinatorClient;
    }

    @PostMapping("/charge")
    public ResponseEntity<String> charge(@RequestHeader("X-XID") String xid, @RequestBody Map<String, String> body
    ) {
        String orderRef = requireOrderRef(body);
        log.info("XID: {} - Processing payment for {}", xid, orderRef);

        Long stepId = coordinatorClient.registerStep(xid, buildStep(orderRef));

        try {
            paymentService.charge(orderRef);
            coordinatorClient.markSuccess(xid, stepId);
        } catch (RuntimeException exception) {
            coordinatorClient.markFailure(xid, stepId, exception.getMessage());
            throw exception;
        }

        return ResponseEntity.ok("Payment charged");
    }

    @PostMapping("/compensate/refund")
    public ResponseEntity<String> refund(@RequestBody Map<String, String> body) {
        paymentService.refund(requireOrderRef(body));
        return ResponseEntity.ok("Refunded");
    }

    private RegisterStepRequest buildStep(String orderRef) {
        RegisterStepRequest step = new RegisterStepRequest();
        String payload = toPayload(orderRef);

        step.setServiceName("payment-service");
        step.setActionName("/payments/charge");
        step.setCompensationName("/payments/compensate/refund");
        step.setStepOrder(2);
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
