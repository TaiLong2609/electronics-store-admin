package com.example.ecomerce.controller;

import com.example.ecomerce.models.Product;
import com.example.ecomerce.models.Voucher;
import com.example.ecomerce.models.VoucherDiscountType;
import com.example.ecomerce.models.VoucherUsageLog;
import com.example.ecomerce.repository.ProductRepository;
import com.example.ecomerce.service.VoucherService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/marketing")
@PreAuthorize("hasAuthority('MARKETING_MANAGE')")
public class MarketingController {

    private final VoucherService voucherService;
    private final ProductRepository productRepository;

    public MarketingController(VoucherService voucherService, ProductRepository productRepository) {
        this.voucherService = voucherService;
        this.productRepository = productRepository;
    }

    @GetMapping("/vouchers")
    public List<VoucherResponse> listVouchers() {
        return voucherService.listVouchers().stream()
                .map(this::toVoucherResponse)
                .toList();
    }

    @PostMapping("/vouchers")
    public ResponseEntity<?> createVoucher(@RequestBody VoucherRequest request) {
        try {
            Voucher created = voucherService.createVoucher(toDraft(request));
            return ResponseEntity.ok(toVoucherResponse(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/vouchers/{voucherId}")
    public ResponseEntity<?> updateVoucher(@PathVariable Long voucherId, @RequestBody VoucherRequest request) {
        try {
            Voucher updated = voucherService.updateVoucher(voucherId, toDraft(request));
            return ResponseEntity.ok(toVoucherResponse(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/vouchers/{voucherId}/usage-logs")
    public ResponseEntity<?> listVoucherUsageLogs(
            @PathVariable Long voucherId,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "fromAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromAt,
            @RequestParam(name = "toAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toAt
    ) {
        try {
            voucherService.getVoucher(voucherId);
            List<VoucherUsageLogResponse> logs = voucherService
                    .listUsageLogs(voucherId, username, fromAt, toAt)
                    .stream()
                    .map(this::toUsageLogResponse)
                    .toList();
            return ResponseEntity.ok(logs);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/vouchers/product-options")
    public List<ProductOptionResponse> searchVoucherProductOptions(
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        List<Product> products;
        if (StringUtils.hasText(keyword)) {
            String term = keyword.trim();
            products = productRepository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCaseOrderByIdAsc(term, term)
                    .stream()
                    .limit(20)
                    .toList();
        } else {
            products = productRepository.findAllByOrderByIdAsc()
                    .stream()
                    .limit(20)
                    .toList();
        }

        return products.stream()
                .map(product -> new ProductOptionResponse(product.getId(), product.getName(), product.getSku()))
                .toList();
    }

    private VoucherService.VoucherDraft toDraft(VoucherRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Du lieu voucher khong hop le");
        }

        VoucherDiscountType discountType = null;
        if (StringUtils.hasText(request.discountType())) {
            try {
                discountType = VoucherDiscountType.valueOf(request.discountType().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("discountType khong hop le. Ho tro: PERCENT, FIXED");
            }
        }

        return new VoucherService.VoucherDraft(
                request.code(),
                request.title(),
                request.description(),
                discountType,
                request.discountValue(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.totalQuota(),
                request.perUserLimit(),
                request.eligibleProductIds() == null ? Set.of() : request.eligibleProductIds().stream()
                        .filter(id -> id != null && id > 0)
                        .collect(Collectors.toSet()),
                request.startAt(),
                request.endAt(),
                request.active()
        );
    }

    private VoucherResponse toVoucherResponse(Voucher voucher) {
        VoucherService.VoucherUsageStats stats = voucherService.getUsageStats(voucher.getId(), voucher.getTotalQuota());
        List<ProductOptionResponse> eligibleProducts = toEligibleProducts(voucher.getEligibleProductIds());
        return new VoucherResponse(
                voucher.getId(),
                voucher.getCode(),
                voucher.getTitle(),
                voucher.getDescription(),
                voucher.getDiscountType() == null ? null : voucher.getDiscountType().name(),
                voucher.getDiscountValue(),
                voucher.getMinOrderAmount(),
                voucher.getMaxDiscountAmount(),
                voucher.getTotalQuota(),
                stats.usedCount(),
                stats.remainingQuota(),
                voucher.getPerUserLimit(),
                voucher.getEligibleProductIds() == null ? List.of() : voucher.getEligibleProductIds().stream().sorted().toList(),
                eligibleProducts,
                voucher.getStartAt() == null ? null : voucher.getStartAt().toString(),
                voucher.getEndAt() == null ? null : voucher.getEndAt().toString(),
                Boolean.TRUE.equals(voucher.getActive()),
                voucher.getCreatedAt() == null ? null : voucher.getCreatedAt().toString(),
                voucher.getUpdatedAt() == null ? null : voucher.getUpdatedAt().toString()
        );
    }

    private List<ProductOptionResponse> toEligibleProducts(Set<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        return productIds.stream()
                .sorted()
                .map(byId::get)
                .filter(product -> product != null)
                .map(product -> new ProductOptionResponse(product.getId(), product.getName(), product.getSku()))
                .toList();
    }

    private VoucherUsageLogResponse toUsageLogResponse(VoucherUsageLog log) {
        return new VoucherUsageLogResponse(
                log.getId(),
                log.getVoucher() == null ? null : log.getVoucher().getId(),
                log.getVoucherCode(),
                log.getUsername(),
                log.getOrderId(),
                log.getOrderAmount(),
                log.getDiscountAmount(),
                log.getFinalAmount(),
                log.getStatus() == null ? null : log.getStatus().name(),
                log.getUsedAt() == null ? null : log.getUsedAt().toString(),
                log.getReleasedAt() == null ? null : log.getReleasedAt().toString(),
                log.getReleaseReason()
        );
    }

    public record VoucherRequest(
            String code,
            String title,
            String description,
            String discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            Integer totalQuota,
            Integer perUserLimit,
            List<Long> eligibleProductIds,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean active
    ) {
    }

    public record VoucherResponse(
            Long id,
            String code,
            String title,
            String description,
            String discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            Integer totalQuota,
            Long usedCount,
            Integer remainingQuota,
            Integer perUserLimit,
            List<Long> eligibleProductIds,
            List<ProductOptionResponse> eligibleProducts,
            String startAt,
            String endAt,
            Boolean active,
            String createdAt,
            String updatedAt
    ) {
    }

    public record VoucherUsageLogResponse(
            Long id,
            Long voucherId,
            String voucherCode,
            String username,
            Long orderId,
            BigDecimal orderAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            String status,
            String usedAt,
            String releasedAt,
            String releaseReason
    ) {
    }

    public record ProductOptionResponse(
            Long id,
            String name,
            String sku
    ) {
    }
}
