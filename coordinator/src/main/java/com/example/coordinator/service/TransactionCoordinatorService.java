package com.example.coordinator.service;

import com.example.coordinator.dto.BeginTransactionResponse;
import com.example.coordinator.dto.RegisterStepRequest;
import com.example.coordinator.dto.StepResultRequest;
import com.example.coordinator.dto.StepStateResponse;
import com.example.coordinator.dto.TransactionStateResponse;
import com.example.coordinator.entity.GlobalTransaction;
import com.example.coordinator.entity.GlobalTxStatus;
import com.example.coordinator.entity.StepStatus;
import com.example.coordinator.entity.TransactionStep;
import com.example.coordinator.exception.StepNotFoundException;
import com.example.coordinator.exception.TransactionNotFoundException;
import com.example.coordinator.repository.GlobalTransactionRepository;
import com.example.coordinator.repository.TransactionStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionCoordinatorService {

    private final GlobalTransactionRepository globalTransactionRepository;
    private final TransactionStepRepository transactionStepRepository;

    public TransactionCoordinatorService(
            GlobalTransactionRepository globalTransactionRepository,
            TransactionStepRepository transactionStepRepository
    ) {
        this.globalTransactionRepository = globalTransactionRepository;
        this.transactionStepRepository = transactionStepRepository;
    }

    @Transactional
    public BeginTransactionResponse beginTransaction() {
        String xid = UUID.randomUUID().toString();

        GlobalTransaction transaction = new GlobalTransaction();
        transaction.setXid(xid);
        transaction.setStatus(GlobalTxStatus.STARTED);
        transaction.setStartedAt(LocalDateTime.now());

        globalTransactionRepository.save(transaction);

        return new BeginTransactionResponse(transaction.getXid(), transaction.getStatus().name());
    }

    @Transactional
    public StepStateResponse registerStep(String xid, RegisterStepRequest request) {
        GlobalTransaction transaction = getTransactionEntity(xid);

        if (transaction.getStatus() == GlobalTxStatus.FAILED) {
            throw new IllegalArgumentException("Cannot register steps; transaction failed. " + xid);
        }

        TransactionStep step = new TransactionStep();
        LocalDateTime now = LocalDateTime.now();
        step.setXid(xid);
        step.setServiceName(request.serviceName());
        step.setActionName(request.actionName());
        step.setCompensationName(request.compensationName());
        step.setStepOrder(request.stepOrder());
        step.setStatus(StepStatus.PENDING);
        step.setRequestPayload(request.requestPayload());
        step.setCompensationPayload(request.compensationPayload());
        step.setCreatedAt(now);
        step.setUpdatedAt(now);

        TransactionStep savedStep = transactionStepRepository.save(step);

        if (transaction.getStatus() == GlobalTxStatus.STARTED) {
            transaction.setStatus(GlobalTxStatus.IN_PROGRESS);
            globalTransactionRepository.save(transaction);
        }

        return toStepState(savedStep);
    }

    @Transactional
    public StepStateResponse markStepSuccess(String xid, Long stepId, StepResultRequest request) {
        TransactionStep step = getStepEntity(stepId, xid);
        step.setStatus(StepStatus.SUCCESS);
        step.setUpdatedAt(LocalDateTime.now());

        return toStepState(transactionStepRepository.save(step));
    }

    @Transactional
    public StepStateResponse markStepFailure(String xid, Long stepId, StepResultRequest request) {
        GlobalTransaction transaction = getTransactionEntity(xid);
        TransactionStep step = getStepEntity(stepId, xid);
        LocalDateTime now = LocalDateTime.now();

        step.setStatus(StepStatus.FAILED);
        step.setUpdatedAt(now);
        transactionStepRepository.save(step);

        transaction.setStatus(GlobalTxStatus.FAILED);
        transaction.setFinishedAt(now);
        transaction.setFailureReason(request.message());
        globalTransactionRepository.save(transaction);

        return toStepState(step);
    }

    @Transactional(readOnly = true)
    public TransactionStateResponse getTransactionState(String xid) {
        GlobalTransaction transaction = getTransactionEntity(xid);
        List<StepStateResponse> steps = transactionStepRepository.findByXidOrderByStepOrderAsc(xid)
                .stream()
                .map(this::toStepState)
                .toList();

        return new TransactionStateResponse(
                transaction.getXid(),
                transaction.getStatus().name(),
                transaction.getFailureReason(),
                transaction.getStartedAt(),
                transaction.getFinishedAt(),
                steps
        );
    }

    private GlobalTransaction getTransactionEntity(String xid) {
        return globalTransactionRepository.findById(xid)
                .orElseThrow(() -> new TransactionNotFoundException(xid));
    }

    private TransactionStep getStepEntity(Long stepId, String xid) {
        TransactionStep step = transactionStepRepository.findById(stepId)
                .orElseThrow(() -> new StepNotFoundException(stepId));

        if (!step.getXid().equals(xid)) {
            throw new IllegalArgumentException("Step " + stepId + " does not belong to transaction " + xid);
        }

        return step;
    }

    private StepStateResponse toStepState(TransactionStep step) {
        return new StepStateResponse(
                step.getStepId(),
                step.getServiceName(),
                step.getActionName(),
                step.getCompensationName(),
                step.getStepOrder(),
                step.getStatus().name()
        );
    }
}
