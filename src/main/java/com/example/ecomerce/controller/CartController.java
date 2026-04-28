package com.example.ecomerce.controller;

import com.example.ecomerce.models.CartItem;
import com.example.ecomerce.models.Product;
import com.example.ecomerce.repository.ProductRepository;
import com.example.ecomerce.service.CartService;
import com.example.ecomerce.service.VoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cart")
public class CartController {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CartService cartService;
    private final ProductRepository productRepository;
    private final VoucherService voucherService;

    @Autowired
    public CartController(CartService cartService, ProductRepository productRepository, VoucherService voucherService) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.voucherService = voucherService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CART_VIEW')")
    public ResponseEntity<?> getCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.getCart(authentication.getName()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CART_MODIFY')")
    public ResponseEntity<?> addToCart(Authentication authentication, @RequestBody CartItem item) {
        if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId and quantity are required"));
        }

        if (!productRepository.existsById(item.getProductId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product not found"));
        }

        return ResponseEntity.ok(cartService.addItem(authentication.getName(), item));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('CART_VIEW')")
    public ResponseEntity<?> getCartSummary(Authentication authentication) {
        return ResponseEntity.ok(buildSummary(authentication.getName()));
    }

    @PutMapping("/voucher")
    @PreAuthorize("hasAuthority('CART_MODIFY')")
    public ResponseEntity<?> applyVoucher(Authentication authentication, @RequestBody ApplyVoucherRequest request) {
        String username = authentication.getName();
        if (request == null || !StringUtils.hasText(request.code())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Voucher code is required"));
        }

        CartComputation computation = computeCart(username);
        if (computation.subtotal().compareTo(ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Gio hang dang trong, khong the ap voucher"));
        }

        VoucherService.VoucherEvaluation evaluation = voucherService.evaluateForPreview(
                username,
                request.code(),
                computation.subtotal(),
                computation.orderLines()
        );
        if (!evaluation.valid()) {
            return ResponseEntity.badRequest().body(Map.of("error", evaluation.message()));
        }

        cartService.applyVoucherCode(username, evaluation.voucher().getCode());
        return ResponseEntity.ok(buildSummary(username));
    }

    @DeleteMapping("/voucher")
    @PreAuthorize("hasAuthority('CART_MODIFY')")
    public ResponseEntity<?> removeVoucher(Authentication authentication) {
        cartService.clearVoucherCode(authentication.getName());
        return ResponseEntity.ok(buildSummary(authentication.getName()));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAuthority('CART_MODIFY')")
    public ResponseEntity<?> removeFromCart(Authentication authentication, @PathVariable Long productId) {
        return ResponseEntity.ok(cartService.removeItem(authentication.getName(), productId));
    }

    private CartSummaryResponse buildSummary(String username) {
        CartComputation computation = computeCart(username);
        List<CartLineResponse> lineResponses = computation.lineResponses();
        BigDecimal subtotal = computation.subtotal();

        String appliedCode = cartService.getVoucherCode(username).orElse(null);
        VoucherService.VoucherEvaluation evaluation = voucherService.evaluateForPreview(
                username,
                appliedCode,
                subtotal,
                computation.orderLines()
        );
        BigDecimal discountAmount = evaluation.valid() ? evaluation.discountAmount() : ZERO;
        BigDecimal totalAmount = subtotal.subtract(discountAmount);
        if (totalAmount.compareTo(ZERO) < 0) {
            totalAmount = ZERO;
        }

        AppliedVoucherResponse voucherResponse = null;
        if (StringUtils.hasText(appliedCode)) {
            voucherResponse = toVoucherResponse(appliedCode, evaluation);
        }

        return new CartSummaryResponse(
                lineResponses,
                subtotal.doubleValue(),
                discountAmount.doubleValue(),
                totalAmount.doubleValue(),
                voucherResponse
        );
    }

    private CartComputation computeCart(String username) {
        List<CartItem> items = cartService.getCart(username);
        Set<Long> productIds = items.stream()
                .map(CartItem::getProductId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, Product> productById = new HashMap<>();
        if (!productIds.isEmpty()) {
            productById = productRepository.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));
        }

        List<CartLineResponse> lineResponses = new ArrayList<>();
        List<VoucherService.OrderLine> orderLines = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        for (CartItem item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }

            Product product = productById.get(item.getProductId());
            BigDecimal unitPrice = toMoney(product == null ? null : product.getPrice());
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            lineResponses.add(new CartLineResponse(
                    item.getProductId(),
                    product == null ? null : product.getName(),
                    product == null ? null : product.getSku(),
                    item.getQuantity(),
                    unitPrice.doubleValue(),
                    lineTotal.doubleValue()
            ));
            orderLines.add(new VoucherService.OrderLine(item.getProductId(), lineTotal));
        }

        return new CartComputation(lineResponses, orderLines, subtotal);
    }

    private AppliedVoucherResponse toVoucherResponse(String appliedCode, VoucherService.VoucherEvaluation evaluation) {
        if (evaluation.voucher() == null) {
            return new AppliedVoucherResponse(
                    appliedCode,
                    null,
                    "INVALID",
                    evaluation.message(),
                    null,
                    null,
                    0d,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        VoucherService.VoucherUsageStats stats = voucherService.getUsageStats(
                evaluation.voucher().getId(),
                evaluation.voucher().getTotalQuota()
        );
        return new AppliedVoucherResponse(
                evaluation.voucher().getCode(),
                evaluation.voucher().getTitle(),
                evaluation.valid() ? "APPLIED" : "INVALID",
                evaluation.message(),
                evaluation.voucher().getDiscountType() == null ? null : evaluation.voucher().getDiscountType().name(),
                evaluation.voucher().getDiscountValue() == null ? null : evaluation.voucher().getDiscountValue().doubleValue(),
                evaluation.discountAmount().doubleValue(),
                evaluation.voucher().getMinOrderAmount() == null ? null : evaluation.voucher().getMinOrderAmount().doubleValue(),
                evaluation.voucher().getMaxDiscountAmount() == null ? null : evaluation.voucher().getMaxDiscountAmount().doubleValue(),
                evaluation.voucher().getTotalQuota(),
                stats.remainingQuota(),
                evaluation.voucher().getPerUserLimit(),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, evaluation.userUsedCount())),
                evaluation.voucher().getStartAt() == null ? null : evaluation.voucher().getStartAt().toString(),
                evaluation.voucher().getEndAt() == null ? null : evaluation.voucher().getEndAt().toString()
        );
    }

    private BigDecimal toMoney(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public record ApplyVoucherRequest(String code) {
    }

    public record CartSummaryResponse(
            List<CartLineResponse> items,
            Double subtotal,
            Double discountAmount,
            Double totalAmount,
            AppliedVoucherResponse voucher
    ) {
    }

    public record CartLineResponse(
            Long productId,
            String name,
            String sku,
            Integer quantity,
            Double unitPrice,
            Double lineTotal
    ) {
    }

    public record AppliedVoucherResponse(
            String code,
            String title,
            String status,
            String message,
            String discountType,
            Double discountValue,
            Double discountAmount,
            Double minOrderAmount,
            Double maxDiscountAmount,
            Integer totalQuota,
            Integer remainingQuota,
            Integer perUserLimit,
            Integer userUsedCount,
            String startAt,
            String endAt
    ) {
    }

    private record CartComputation(
            List<CartLineResponse> lineResponses,
            List<VoucherService.OrderLine> orderLines,
            BigDecimal subtotal
    ) {
    }
}
