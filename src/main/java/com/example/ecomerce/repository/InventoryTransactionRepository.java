package com.example.ecomerce.repository;

import com.example.ecomerce.models.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<InventoryTransaction> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime start, LocalDateTime end);

    List<InventoryTransaction> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(LocalDateTime start);

    List<InventoryTransaction> findByCreatedAtLessThanEqualOrderByCreatedAtAsc(LocalDateTime end);

    List<InventoryTransaction> findByCreatedAtBeforeOrderByCreatedAtDesc(LocalDateTime before);

    List<InventoryTransaction> findAllByOrderByCreatedAtAsc();

    List<InventoryTransaction> findTop200ByOrderByCreatedAtDesc();

    void deleteByProductId(Long productId);
}
