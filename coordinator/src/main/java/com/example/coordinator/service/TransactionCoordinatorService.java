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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionCoordinatorService {

    private final GlobalTransactionRepository globalTransactionRepository;
    private final TransactionStepRepository transactionStepRepository;
    private final RestTemplate restTemplate;

    public TransactionCoordinatorService(GlobalTransactionRepository globalTransactionRepository, TransactionStepRepository transactionStepRepository, RestTemplate restTemplate
    ) {
        this.globalTransactionRepository = globalTransactionRepository;
        this.transactionStepRepository = transactionStepRepository;
        this.restTemplate = restTemplate;
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

        if (transaction.getStatus() != GlobalTxStatus.STARTED && transaction.getStatus() != GlobalTxStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Cannot register steps for transaction state " + transaction.getStatus() + ". " + xid);
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

        transaction.setStatus(GlobalTxStatus.COMPENSATING);
        transaction.setFailureReason(request.message());
        globalTransactionRepository.save(transaction);

        compensate(xid);

        return toStepState(step);
    }

    @Transactional
    public void compensate(String xid) {
        List<TransactionStep> steps = transactionStepRepository.findByXidOrderByStepOrderDesc(xid);

        for (TransactionStep step : steps) {
            if (step.getStatus() != StepStatus.SUCCESS) {
                continue;
            }

            try {
                callCompensation(step);
                step.setStatus(StepStatus.COMPENSATED);
            } catch (RuntimeException exception) {
                step.setStatus(StepStatus.COMPENSATION_FAILED);
            }

            step.setUpdatedAt(LocalDateTime.now());
            transactionStepRepository.save(step);
        }

        GlobalTransaction transaction = getTransactionEntity(xid);
        transaction.setStatus(GlobalTxStatus.COMPENSATED);
        transaction.setFinishedAt(LocalDateTime.now());
        globalTransactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionStateResponse getTransactionState(String xid) {
        GlobalTransaction transaction = getTransactionEntity(xid);
        List<StepStateResponse> steps = transactionStepRepository.findByXidOrderByStepOrderAsc(xid)
                .stream()
                .map(this::toStepState)
                .toList();

        return new TransactionStateResponse(transaction.getXid(), transaction.getStatus().name(), transaction.getFailureReason(), transaction.getStartedAt(), transaction.getFinishedAt(), steps);
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

    private void callCompensation(TransactionStep step) {
        String url = "http://localhost:" + resolvePort(step.getServiceName()) + step.getCompensationName();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = step.getCompensationPayload();
        HttpEntity<String> request = new HttpEntity<>(payload == null ? "{}" : payload, headers);

        restTemplate.postForObject(url, request, String.class);
    }

    private int resolvePort(String serviceName) {
        return switch (serviceName) {
            case "order-service" -> 8083;
            case "payment-service" -> 8084;
            case "inventory-service" -> 8085;
            default -> throw new RuntimeException("Unknown service: " + serviceName);
        };
    }

    private StepStateResponse toStepState(TransactionStep step) {
        return new StepStateResponse(step.getStepId(), step.getServiceName(), step.getActionName(), step.getCompensationName(), step.getStepOrder(), step.getStatus().name());
    }
}
