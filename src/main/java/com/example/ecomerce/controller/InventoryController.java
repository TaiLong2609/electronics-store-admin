package com.example.ecomerce.controller;

import com.example.ecomerce.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stocks")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public Object listStocks(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "attributeKeyword", required = false) String attributeKeyword
    ) {
        return inventoryService.listStocks(keyword, categoryId, attributeKeyword);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public Object listLowStock(@RequestParam(name = "threshold", required = false) Integer threshold) {
        return inventoryService.listLowStock(threshold);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public Object listCategoryOptions() {
        return inventoryService.listCategories();
    }

    @GetMapping("/reports/summary")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ResponseEntity<?> getStockReport(
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "attributeKeyword", required = false) String attributeKeyword,
            @RequestParam(name = "lowStockOnly", required = false) Boolean lowStockOnly
    ) {
        try {
            return ResponseEntity.ok(inventoryService.buildStockReport(fromDate, toDate, categoryId, attributeKeyword, lowStockOnly));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')")
    public Object listOrdersForFulfillment() {
        return inventoryService.listOrdersForFulfillment();
    }

    @GetMapping("/ledger/{sku}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public Object getProductLedger(@PathVariable String sku) {
        try {
            return inventoryService.getLedgerBySku(sku);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/inbound")
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')")
    public ResponseEntity<?> createInbound(
            Authentication authentication,
            @RequestBody InboundRequest request
    ) {
        try {
            var response = inventoryService.receiveInbound(
                    actor(authentication),
                    new InventoryService.InboundRequest(
                            request.sku(),
                            request.quantity(),
                            request.referenceCode(),
                            request.location(),
                            request.note()
                    )
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/manual-products")
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')")
    public ResponseEntity<?> manualAddProduct(
            Authentication authentication,
            @RequestBody ManualAddRequest request
    ) {
        try {
            var response = inventoryService.manualAddProduct(
                    actor(authentication),
                    new InventoryService.ManualAddRequest(
                            request.name(),
                            request.mpn(),
                            request.description(),
                            request.price(),
                            request.categoryId(),
                            request.barcode(),
                            request.warrantyMonths(),
                            request.initialQuantity(),
                            request.reorderLevel(),
                            request.storageLocation()
                    )
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PatchMapping("/products/{productId}/location")
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')")
    public ResponseEntity<?> updateLocation(
            Authentication authentication,
            @PathVariable Long productId,
            @RequestBody LocationUpdateRequest request
    ) {
        try {
            var response = inventoryService.updateLocation(
                    actor(authentication),
                    productId,
                    new InventoryService.LocationUpdateRequest(request.storageLocation(), request.note())
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/outbound")
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')")
    public ResponseEntity<?> createOutbound(
            Authentication authentication,
            @RequestBody OutboundRequest request
    ) {
        try {
            var response = inventoryService.fulfillOrder(
                    actor(authentication),
                    new InventoryService.OutboundRequest(request.orderId(), request.note())
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/returns")
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')")
    public ResponseEntity<?> processReturn(
            Authentication authentication,
            @RequestBody ReturnRequest request
    ) {
        try {
            var response = inventoryService.processReturn(
                    actor(authentication),
                    new InventoryService.ReturnRequest(request.orderId(), request.quantity(), request.note())
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')")
    public ResponseEntity<?> adjustStock(
            Authentication authentication,
            @RequestBody AdjustmentRequest request
    ) {
        try {
            var response = inventoryService.adjustStock(
                    actor(authentication),
                    new InventoryService.AdjustmentRequest(request.productId(), request.deltaQuantity(), request.note())
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    public record InboundRequest(
            String sku,
            Integer quantity,
            String referenceCode,
            String location,
            String note
    ) {
    }

    public record ManualAddRequest(
            String name,
            String mpn,
            String description,
            Double price,
            Long categoryId,
            String barcode,
            Integer warrantyMonths,
            Integer initialQuantity,
            Integer reorderLevel,
            String storageLocation
    ) {
    }

    public record LocationUpdateRequest(String storageLocation, String note) {
    }

    public record OutboundRequest(Long orderId, String note) {
    }

    public record ReturnRequest(Long orderId, Integer quantity, String note) {
    }

    public record AdjustmentRequest(Long productId, Integer deltaQuantity, String note) {
    }
}
