package com.example.inventoryservice.dto;

public class RegisterStepRequest {

    private String serviceName;
    private String actionName;
    private String compensationName;
    private Integer stepOrder;
    private String requestPayload;
    private String compensationPayload;

    public RegisterStepRequest() {
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getCompensationName() {
        return compensationName;
    }

    public void setCompensationName(String compensationName) {
        this.compensationName = compensationName;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getCompensationPayload() {
        return compensationPayload;
    }

    public void setCompensationPayload(String compensationPayload) {
        this.compensationPayload = compensationPayload;
    }
}
