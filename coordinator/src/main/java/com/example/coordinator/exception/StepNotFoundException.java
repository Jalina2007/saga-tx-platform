package com.example.coordinator.exception;

public class StepNotFoundException extends RuntimeException {

    public StepNotFoundException(Long stepId) {
        super("Step not found: " + stepId);
    }
}
