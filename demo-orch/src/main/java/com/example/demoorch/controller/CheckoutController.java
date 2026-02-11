package com.example.demoorch.controller;

import com.example.demoorch.service.CheckoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<String> checkout(@RequestBody Map<String, String> body) {
        checkoutService.process(requireOrderRef(body));
        return ResponseEntity.ok("Checkout complete");
    }

    private String requireOrderRef(Map<String, String> body) {
        String orderRef = body.get("orderRef");

        if (orderRef == null || orderRef.isBlank()) {
            throw new IllegalArgumentException("orderRef is required");
        }

        return orderRef;
    }
}
