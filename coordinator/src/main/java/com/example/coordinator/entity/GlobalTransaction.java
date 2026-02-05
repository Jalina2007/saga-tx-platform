package com.example.coordinator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "global_tx")
public class GlobalTransaction {

    @Id
    private String xid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GlobalTxStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    public GlobalTransaction() {
    }

    public GlobalTransaction(String xid, GlobalTxStatus status, LocalDateTime startedAt) {
        this.xid = xid;
        this.status = status;
        this.startedAt = startedAt;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public GlobalTxStatus getStatus() {
        return status;
    }

    public void setStatus(GlobalTxStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
