package com.example.paymentservice.dto;

public class StepResultRequest {

    private String status;
    private String message;

    public StepResultRequest() {
    }

    public StepResultRequest(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
