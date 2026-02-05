package com.example.coordinator.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String xid) {
        super("Transaction not found: " + xid);
    }
}
