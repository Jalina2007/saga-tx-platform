package com.example.orderservice.service;

import com.example.orderservice.entity.Order;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(String orderRef) {
        Order order = new Order();
        order.setOrderRef(orderRef);
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());

        return orderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(String orderRef) {
        Order order = orderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderRef));

        if ("CANCELLED".equals(order.getStatus())) {
            return;
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }
}
