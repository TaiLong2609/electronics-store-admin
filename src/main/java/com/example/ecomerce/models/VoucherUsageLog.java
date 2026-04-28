package com.example.ecomerce.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_usage_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Voucher voucher;

    @Column(nullable = false, length = 64)
    private String voucherCode;

    @Column(nullable = false, length = 120)
    private String username;

    @Column
    private Long orderId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal orderAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoucherUsageStatus status;

    @Column(nullable = false)
    private LocalDateTime usedAt;

    @Column
    private LocalDateTime releasedAt;

    @Column(length = 255)
    private String releaseReason;

    @PrePersist
    void onCreate() {
        if (usedAt == null) {
            usedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = VoucherUsageStatus.USED;
        }
    }
}

