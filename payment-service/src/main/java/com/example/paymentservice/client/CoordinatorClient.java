package com.example.paymentservice.client;

import com.example.paymentservice.dto.RegisterStepRequest;
import com.example.paymentservice.dto.StepResultRequest;
import com.example.paymentservice.dto.StepStateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CoordinatorClient {

    private final RestTemplate restTemplate;
    private final String coordinatorBaseUrl;

    public CoordinatorClient(RestTemplate restTemplate, @Value("${coordinator.base-url:http://localhost:8081}") String coordinatorBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.coordinatorBaseUrl = coordinatorBaseUrl;
    }

    public Long registerStep(String xid, RegisterStepRequest request) {
        String url = coordinatorBaseUrl + "/api/tx/" + xid + "/steps";
        StepStateResponse response = restTemplate.postForObject(url, request, StepStateResponse.class);

        if (response == null || response.getStepId() == null) {
            throw new IllegalStateException("Coordinator did not return a step id");
        }

        return response.getStepId();
    }

    public void markSuccess(String xid, Long stepId) {
        updateStep(xid, stepId, new StepResultRequest("SUCCESS", "OK"), "success");
    }

    public void markFailure(String xid, Long stepId, String message) {
        updateStep(xid, stepId, new StepResultRequest("FAILED", message), "failure");
    }

    private void updateStep(String xid, Long stepId, StepResultRequest request, String outcome) {
        String url = coordinatorBaseUrl + "/api/tx/" + xid + "/steps/" + stepId + "/" + outcome;
        ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request), Void.class);

        if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Coordinator step update failed");
    }
}
