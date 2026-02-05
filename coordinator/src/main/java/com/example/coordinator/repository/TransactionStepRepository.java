package com.example.coordinator.repository;

import com.example.coordinator.entity.TransactionStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionStepRepository extends JpaRepository<TransactionStep, Long> {

    List<TransactionStep> findByXidOrderByStepOrderAsc(String xid);
}
