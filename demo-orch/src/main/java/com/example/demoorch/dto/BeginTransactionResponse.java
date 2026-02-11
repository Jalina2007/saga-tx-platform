package com.example.demoorch.dto;

public class BeginTransactionResponse {

    private String xid;
    private String status;

    public BeginTransactionResponse() {
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
