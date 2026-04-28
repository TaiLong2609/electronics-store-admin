package com.example.ecomerce.repository;

import com.example.ecomerce.models.VoucherUsageLog;
import com.example.ecomerce.models.VoucherUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherUsageLogRepository extends JpaRepository<VoucherUsageLog, Long> {

    long countByVoucher_IdAndStatus(Long voucherId, VoucherUsageStatus status);

    long countByVoucher_IdAndUsernameIgnoreCaseAndStatus(Long voucherId, String username, VoucherUsageStatus status);

    boolean existsByOrderIdAndStatus(Long orderId, VoucherUsageStatus status);

    List<VoucherUsageLog> findByOrderIdAndStatus(Long orderId, VoucherUsageStatus status);

    List<VoucherUsageLog> findByVoucher_IdOrderByUsedAtDesc(Long voucherId);

    List<VoucherUsageLog> findAllByOrderByUsedAtDesc();
}

