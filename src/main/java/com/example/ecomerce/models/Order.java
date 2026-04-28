package com.example.ecomerce.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long id;
    private String username;
    private Long productId;
    private Integer quantity;
    private String status;
    private Double unitPrice;
    private Double subtotalAmount;
    private String voucherCode;
    private Double discountAmount;
    private Double payableAmount;
}
