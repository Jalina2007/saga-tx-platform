package com.example.coordinator.repository;

import com.example.coordinator.entity.GlobalTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalTransactionRepository extends JpaRepository<GlobalTransaction, String> {
}
