package com.example.ecomerce.repository;

import com.example.ecomerce.models.FinancialTransaction;
import com.example.ecomerce.models.TransactionStatus;
import com.example.ecomerce.models.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    List<FinancialTransaction> findAllByOrderByCreatedAtDesc();

    Optional<FinancialTransaction> findFirstByOrderIdAndTypeAndStatusOrderByIdAsc(
            Long orderId,
            TransactionType type,
            TransactionStatus status
    );

    boolean existsByRelatedTransactionId(Long relatedTransactionId);
}

