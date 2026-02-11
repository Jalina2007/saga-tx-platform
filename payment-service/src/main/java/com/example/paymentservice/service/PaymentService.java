package com.example.paymentservice.service;

import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void charge(String orderRef) {
        Payment payment = new Payment();
        payment.setOrderRef(orderRef);
        payment.setStatus("CHARGED");
        payment.setCreatedAt(LocalDateTime.now());

        paymentRepository.save(payment);
    }

    @Transactional
    public void refund(String orderRef) {
        Payment payment = paymentRepository.findByOrderRef(orderRef).orElseThrow(() -> new RuntimeException("Payment not found: " + orderRef));

        if ("REFUNDED".equals(payment.getStatus())) return;

        payment.setStatus("REFUNDED");
        paymentRepository.save(payment);
    }
}
