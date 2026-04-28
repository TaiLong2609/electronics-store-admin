package com.example.ecomerce.controller;

import com.example.ecomerce.models.Order;
import com.example.ecomerce.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORDER_VIEW_SELF', 'ORDER_VIEW_ALL')")
    public List<Order> getOrders(Authentication authentication) {
        return orderService.listOrdersFor(authentication);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    public ResponseEntity<?> createOrder(Authentication authentication, @RequestBody CreateOrderRequest request) {
        if (request.productId() == null || request.quantity() == null || request.quantity() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId and quantity are required"));
        }

        try {
            var order = orderService.createOrder(
                    authentication.getName(),
                    request.productId(),
                    request.quantity(),
                    request.voucherCode()
            );
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasAuthority('ORDER_MODIFY_STATUS')")
    public ResponseEntity<?> updateOrderStatus(
            Authentication authentication,
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request
    ) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        try {
            var order = orderService.updateStatus(orderId, request.status(), authentication == null ? null : authentication.getName());
            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    public record CreateOrderRequest(Long productId, Integer quantity, String voucherCode) {
    }

    public record UpdateOrderStatusRequest(String status) {
    }
}
