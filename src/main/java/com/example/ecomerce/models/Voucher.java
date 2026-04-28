package com.example.ecomerce.models;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "vouchers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vouchers_code", columnNames = "code")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoucherDiscountType discountType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 18, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column
    private Integer totalQuota;

    @Column
    private Integer perUserLimit;

    @ElementCollection
    @CollectionTable(
            name = "voucher_eligible_products",
            joinColumns = @JoinColumn(name = "voucher_id")
    )
    @Column(name = "product_id", nullable = false)
    private Set<Long> eligibleProductIds = new HashSet<>();

    @Column(nullable = false)
    private Boolean active;

    @Column
    private LocalDateTime startAt;

    @Column
    private LocalDateTime endAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = createdAt;
        if (active == null) {
            active = Boolean.TRUE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = Boolean.TRUE;
        }
    }
}

