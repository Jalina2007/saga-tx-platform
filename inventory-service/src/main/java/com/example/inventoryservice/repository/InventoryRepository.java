package com.example.inventoryservice.repository;

import com.example.inventoryservice.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByOrderRef(String orderRef);
}
