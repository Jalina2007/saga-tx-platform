package com.example.demoorch.service;

import com.example.demoorch.dto.BeginTransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final RestTemplate restTemplate;
    private final String coordinatorBaseUrl;
    private final String orderServiceBaseUrl;
    private final String paymentServiceBaseUrl;

    public CheckoutService(
            RestTemplate restTemplate,
            @Value("${coordinator.base-url:http://localhost:8081}") String coordinatorBaseUrl,
            @Value("${order-service.base-url:http://localhost:8083}") String orderServiceBaseUrl,
            @Value("${payment-service.base-url:http://localhost:8084}") String paymentServiceBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.coordinatorBaseUrl = coordinatorBaseUrl;
        this.orderServiceBaseUrl = orderServiceBaseUrl;
        this.paymentServiceBaseUrl = paymentServiceBaseUrl;
    }

    public void process(String orderRef) {
        BeginTransactionResponse transaction = restTemplate.postForObject(
                coordinatorBaseUrl + "/api/tx/begin",
                null,
                BeginTransactionResponse.class
        );

        if (transaction == null || transaction.getXid() == null || transaction.getXid().isBlank()) {
            throw new IllegalStateException("Coordinator did not return an XID");
        }

        String xid = transaction.getXid();
        log.info("XID: {} - Processing checkout for {}", xid, orderRef);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-XID", xid);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("orderRef", orderRef), headers);

        restTemplate.postForObject(orderServiceBaseUrl + "/orders", request, String.class);
        restTemplate.postForObject(paymentServiceBaseUrl + "/payments/charge", request, String.class);
    }
}
