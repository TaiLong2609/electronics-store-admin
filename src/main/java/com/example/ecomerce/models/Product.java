package com.example.ecomerce.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Double price;
    private String description;

    private String mpn;
    private String sku;
    private String barcode;
    private Integer warrantyMonths;
    private Integer inventoryQuantity;
    private Integer reservedQuantity;
    private String storageLocation;
    private Integer reorderLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Category category;

    @PrePersist
    @PreUpdate
    void normalizeStockFields() {
        if (inventoryQuantity == null) {
            inventoryQuantity = 0;
        }
        if (reservedQuantity == null || reservedQuantity < 0) {
            reservedQuantity = 0;
        }
        if (reservedQuantity > inventoryQuantity) {
            reservedQuantity = inventoryQuantity;
        }
        if (reorderLevel == null || reorderLevel <= 0) {
            reorderLevel = 10;
        }
    }
}
