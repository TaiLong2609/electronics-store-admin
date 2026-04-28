package com.example.ecomerce.controller;

import com.example.ecomerce.service.VoucherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vouchers")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping("/validate")
    @PreAuthorize("hasAnyAuthority('CART_VIEW', 'ORDER_CREATE')")
    public ResponseEntity<?> validateVoucher(
            Authentication authentication,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "orderAmount", required = false) Double orderAmount,
            @RequestParam(name = "productId", required = false) Long productId
    ) {
        if (!StringUtils.hasText(code)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Voucher code is required"));
        }

        BigDecimal normalizedOrderAmount = toMoney(orderAmount);
        List<VoucherService.OrderLine> orderLines = List.of();
        if (productId != null && productId > 0 && normalizedOrderAmount.compareTo(BigDecimal.ZERO) > 0) {
            orderLines = List.of(new VoucherService.OrderLine(productId, normalizedOrderAmount));
        }

        VoucherService.VoucherEvaluation evaluation = voucherService.evaluateForPreview(
                authentication == null ? null : authentication.getName(),
                code,
                normalizedOrderAmount,
                orderLines
        );
        return ResponseEntity.ok(new VoucherValidationResponse(
                evaluation.valid(),
                evaluation.message(),
                evaluation.voucher() == null ? null : evaluation.voucher().getCode(),
                evaluation.voucher() == null ? null : evaluation.voucher().getTitle(),
                evaluation.voucher() == null || evaluation.voucher().getDiscountType() == null
                        ? null
                        : evaluation.voucher().getDiscountType().name(),
                evaluation.voucher() == null || evaluation.voucher().getDiscountValue() == null
                        ? null
                        : evaluation.voucher().getDiscountValue().doubleValue(),
                evaluation.orderAmount().doubleValue(),
                evaluation.discountAmount().doubleValue(),
                evaluation.finalAmount().doubleValue(),
                evaluation.voucher() == null ? null : evaluation.voucher().getMinOrderAmount() == null
                        ? null
                        : evaluation.voucher().getMinOrderAmount().doubleValue(),
                evaluation.voucher() == null ? null : evaluation.voucher().getMaxDiscountAmount() == null
                        ? null
                        : evaluation.voucher().getMaxDiscountAmount().doubleValue(),
                evaluation.voucher() == null ? null : evaluation.voucher().getTotalQuota(),
                evaluation.voucher() == null ? null : evaluation.voucher().getPerUserLimit(),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, evaluation.usedCount())),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, evaluation.userUsedCount())),
                evaluation.voucher() == null || evaluation.voucher().getStartAt() == null
                        ? null
                        : evaluation.voucher().getStartAt().toString(),
                evaluation.voucher() == null || evaluation.voucher().getEndAt() == null
                        ? null
                        : evaluation.voucher().getEndAt().toString()
        ));
    }

    private BigDecimal toMoney(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public record VoucherValidationResponse(
            boolean valid,
            String message,
            String code,
            String title,
            String discountType,
            Double discountValue,
            Double orderAmount,
            Double discountAmount,
            Double finalAmount,
            Double minOrderAmount,
            Double maxDiscountAmount,
            Integer totalQuota,
            Integer perUserLimit,
            Integer usedCount,
            Integer userUsedCount,
            String startAt,
            String endAt
    ) {
    }
}

