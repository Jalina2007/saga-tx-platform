package com.example.inventoryservice.service;

import com.example.inventoryservice.entity.InventoryReservation;
import com.example.inventoryservice.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public void reserve(String orderRef) {
        if (orderRef.contains("FAIL")) {
            throw new RuntimeException("Inventory unavailable");
        }

        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderRef(orderRef);
        reservation.setStatus("RESERVED");
        reservation.setCreatedAt(LocalDateTime.now());

        inventoryRepository.save(reservation);
    }

    @Transactional
    public void release(String orderRef) {
        InventoryReservation reservation = inventoryRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new RuntimeException("Inventory reservation not found: " + orderRef));

        if ("RELEASED".equals(reservation.getStatus())) {
            return;
        }

        reservation.setStatus("RELEASED");
        inventoryRepository.save(reservation);
    }
}
