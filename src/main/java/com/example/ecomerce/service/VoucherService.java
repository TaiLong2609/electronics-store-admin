package com.example.ecomerce.service;

import com.example.ecomerce.models.Voucher;
import com.example.ecomerce.models.VoucherDiscountType;
import com.example.ecomerce.models.VoucherUsageLog;
import com.example.ecomerce.models.VoucherUsageStatus;
import com.example.ecomerce.repository.ProductRepository;
import com.example.ecomerce.repository.VoucherRepository;
import com.example.ecomerce.repository.VoucherUsageLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class VoucherService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductRepository productRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherUsageLogRepository usageLogRepository;

    public VoucherService(
            ProductRepository productRepository,
            VoucherRepository voucherRepository,
            VoucherUsageLogRepository usageLogRepository
    ) {
        this.productRepository = productRepository;
        this.voucherRepository = voucherRepository;
        this.usageLogRepository = usageLogRepository;
    }

    @Transactional(readOnly = true)
    public List<Voucher> listVouchers() {
        return voucherRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Voucher getVoucher(Long voucherId) {
        return voucherRepository.findById(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay voucher: " + voucherId));
    }

    @Transactional
    public Voucher createVoucher(VoucherDraft draft) {
        Voucher voucher = new Voucher();
        applyDraft(voucher, draft, null);
        return voucherRepository.save(voucher);
    }

    @Transactional
    public Voucher updateVoucher(Long voucherId, VoucherDraft draft) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay voucher: " + voucherId));
        applyDraft(voucher, draft, voucherId);
        return voucherRepository.save(voucher);
    }

    @Transactional(readOnly = true)
    public VoucherEvaluation evaluateForPreview(String username, String rawCode, BigDecimal orderAmount) {
        return evaluateForPreview(username, rawCode, orderAmount, List.of());
    }

    @Transactional(readOnly = true)
    public VoucherEvaluation evaluateForPreview(
            String username,
            String rawCode,
            BigDecimal orderAmount,
            List<OrderLine> orderLines
    ) {
        BigDecimal normalizedAmount = normalizeAmount(orderAmount);
        if (!StringUtils.hasText(rawCode)) {
            return new VoucherEvaluation(
                    false,
                    "Chua ap dung voucher",
                    null,
                    normalizedAmount,
                    ZERO,
                    normalizedAmount,
                    0,
                    0
            );
        }

        Optional<Voucher> optionalVoucher = voucherRepository.findByCodeIgnoreCase(rawCode.trim());
        if (optionalVoucher.isEmpty()) {
            return new VoucherEvaluation(
                    false,
                    "Voucher khong ton tai",
                    null,
                    normalizedAmount,
                    ZERO,
                    normalizedAmount,
                    0,
                    0
            );
        }

        return evaluateVoucher(optionalVoucher.get(), username, normalizedAmount, safeLines(orderLines), LocalDateTime.now());
    }

    @Transactional
    public AppliedVoucher consumeForOrder(
            String username,
            String rawCode,
            BigDecimal orderAmount,
            Long orderId,
            List<OrderLine> orderLines
    ) {
        BigDecimal normalizedAmount = normalizeAmount(orderAmount);
        if (!StringUtils.hasText(rawCode)) {
            return AppliedVoucher.none(normalizedAmount);
        }
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId khong hop le de luu log voucher");
        }
        if (usageLogRepository.existsByOrderIdAndStatus(orderId, VoucherUsageStatus.USED)) {
            throw new IllegalArgumentException("Don hang da ap dung voucher truoc do");
        }

        Voucher voucher = voucherRepository.findByCodeForUpdate(rawCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Voucher khong ton tai"));
        VoucherEvaluation evaluation = evaluateVoucher(
                voucher,
                username,
                normalizedAmount,
                safeLines(orderLines),
                LocalDateTime.now()
        );
        if (!evaluation.valid()) {
            throw new IllegalArgumentException(evaluation.message());
        }

        VoucherUsageLog log = new VoucherUsageLog();
        log.setVoucher(voucher);
        log.setVoucherCode(voucher.getCode());
        log.setUsername(normalizeUsername(username));
        log.setOrderId(orderId);
        log.setOrderAmount(normalizedAmount);
        log.setDiscountAmount(evaluation.discountAmount());
        log.setFinalAmount(evaluation.finalAmount());
        log.setStatus(VoucherUsageStatus.USED);
        log.setUsedAt(LocalDateTime.now());
        usageLogRepository.save(log);

        return new AppliedVoucher(
                voucher.getCode(),
                voucher.getTitle(),
                voucher.getDiscountType().name(),
                normalizedAmount,
                evaluation.discountAmount(),
                evaluation.finalAmount()
        );
    }

    @Transactional
    public void releaseForOrder(Long orderId, String reason) {
        if (orderId == null || orderId <= 0) {
            return;
        }

        List<VoucherUsageLog> activeLogs = usageLogRepository.findByOrderIdAndStatus(orderId, VoucherUsageStatus.USED);
        if (activeLogs.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String releaseReason = StringUtils.hasText(reason) ? reason.trim() : "ORDER_CANCELLED";
        for (VoucherUsageLog log : activeLogs) {
            log.setStatus(VoucherUsageStatus.RELEASED);
            log.setReleasedAt(now);
            log.setReleaseReason(releaseReason);
        }
        usageLogRepository.saveAll(activeLogs);
    }

    @Transactional(readOnly = true)
    public VoucherUsageStats getUsageStats(Long voucherId, Integer totalQuota) {
        long usedCount = usageLogRepository.countByVoucher_IdAndStatus(voucherId, VoucherUsageStatus.USED);
        Integer remainingQuota = null;
        if (totalQuota != null && totalQuota > 0) {
            remainingQuota = Math.max(0, totalQuota - Math.toIntExact(Math.min(Integer.MAX_VALUE, usedCount)));
        }
        return new VoucherUsageStats(usedCount, remainingQuota);
    }

    @Transactional(readOnly = true)
    public List<VoucherUsageLog> listUsageLogs(Long voucherId, String username, LocalDateTime fromAt, LocalDateTime toAt) {
        List<VoucherUsageLog> base = voucherId == null
                ? usageLogRepository.findAllByOrderByUsedAtDesc()
                : usageLogRepository.findByVoucher_IdOrderByUsedAtDesc(voucherId);

        String usernameFilter = StringUtils.hasText(username) ? username.trim().toLowerCase(Locale.ROOT) : null;
        return base.stream()
                .filter(item -> usernameFilter == null
                        || (item.getUsername() != null && item.getUsername().trim().toLowerCase(Locale.ROOT).equals(usernameFilter)))
                .filter(item -> fromAt == null || (item.getUsedAt() != null && !item.getUsedAt().isBefore(fromAt)))
                .filter(item -> toAt == null || (item.getUsedAt() != null && !item.getUsedAt().isAfter(toAt)))
                .toList();
    }

    private VoucherEvaluation evaluateVoucher(
            Voucher voucher,
            String username,
            BigDecimal orderAmount,
            List<OrderLine> orderLines,
            LocalDateTime now
    ) {
        long usedCount = usageLogRepository.countByVoucher_IdAndStatus(voucher.getId(), VoucherUsageStatus.USED);
        long userUsedCount = StringUtils.hasText(username)
                ? usageLogRepository.countByVoucher_IdAndUsernameIgnoreCaseAndStatus(voucher.getId(), username.trim(), VoucherUsageStatus.USED)
                : 0;
        BigDecimal eligibleAmount = resolveEligibleAmount(voucher, orderAmount, orderLines);

        if (!Boolean.TRUE.equals(voucher.getActive())) {
            return invalid("Voucher dang tat", voucher, orderAmount, usedCount, userUsedCount);
        }
        if (voucher.getStartAt() != null && now.isBefore(voucher.getStartAt())) {
            return invalid("Voucher chua den thoi gian ap dung", voucher, orderAmount, usedCount, userUsedCount);
        }
        if (voucher.getEndAt() != null && now.isAfter(voucher.getEndAt())) {
            return invalid("Voucher da het han", voucher, orderAmount, usedCount, userUsedCount);
        }
        if (voucher.getTotalQuota() != null && voucher.getTotalQuota() > 0 && usedCount >= voucher.getTotalQuota()) {
            return invalid("Voucher da het luot su dung", voucher, orderAmount, usedCount, userUsedCount);
        }
        if (voucher.getPerUserLimit() != null
                && voucher.getPerUserLimit() > 0
                && StringUtils.hasText(username)
                && userUsedCount >= voucher.getPerUserLimit()) {
            return invalid("Tai khoan da dung het luot voucher", voucher, orderAmount, usedCount, userUsedCount);
        }
        if (eligibleAmount.compareTo(ZERO) <= 0 && hasEligibleProducts(voucher)) {
            return invalid("Voucher khong ap dung cho san pham da chon", voucher, orderAmount, usedCount, userUsedCount);
        }
        if (voucher.getMinOrderAmount() != null && eligibleAmount.compareTo(voucher.getMinOrderAmount()) < 0) {
            return invalid("Don hang chua dat gia tri toi thieu", voucher, orderAmount, usedCount, userUsedCount);
        }

        BigDecimal discount = calculateDiscount(voucher, eligibleAmount);
        BigDecimal finalAmount = normalizeAmount(orderAmount.subtract(discount));
        return new VoucherEvaluation(
                true,
                "Voucher hop le",
                voucher,
                orderAmount,
                discount,
                finalAmount,
                usedCount,
                userUsedCount
        );
    }

    private VoucherEvaluation invalid(String message, Voucher voucher, BigDecimal orderAmount, long usedCount, long userUsedCount) {
        return new VoucherEvaluation(
                false,
                message,
                voucher,
                orderAmount,
                ZERO,
                orderAmount,
                usedCount,
                userUsedCount
        );
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal orderAmount) {
        if (orderAmount.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        BigDecimal discount = ZERO;
        if (voucher.getDiscountType() == VoucherDiscountType.PERCENT) {
            discount = orderAmount
                    .multiply(voucher.getDiscountValue())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        } else if (voucher.getDiscountType() == VoucherDiscountType.FIXED) {
            discount = normalizeAmount(voucher.getDiscountValue());
        }

        if (voucher.getMaxDiscountAmount() != null && voucher.getMaxDiscountAmount().compareTo(ZERO) > 0) {
            discount = discount.min(voucher.getMaxDiscountAmount());
        }
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }
        if (discount.compareTo(ZERO) < 0) {
            return ZERO;
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasEligibleProducts(Voucher voucher) {
        return voucher.getEligibleProductIds() != null && !voucher.getEligibleProductIds().isEmpty();
    }

    private BigDecimal resolveEligibleAmount(Voucher voucher, BigDecimal orderAmount, List<OrderLine> orderLines) {
        if (!hasEligibleProducts(voucher)) {
            return normalizeAmount(orderAmount);
        }

        if (orderLines == null || orderLines.isEmpty()) {
            return ZERO;
        }

        Set<Long> allowedProducts = voucher.getEligibleProductIds();
        BigDecimal eligibleAmount = ZERO;
        for (OrderLine line : orderLines) {
            if (line == null || line.productId() == null || line.productId() <= 0) {
                continue;
            }
            if (!allowedProducts.contains(line.productId())) {
                continue;
            }
            eligibleAmount = eligibleAmount.add(normalizeAmount(line.lineAmount()));
        }
        return normalizeAmount(eligibleAmount);
    }

    private List<OrderLine> safeLines(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .filter(item -> item != null && item.productId() != null && item.productId() > 0)
                .map(item -> new OrderLine(item.productId(), normalizeAmount(item.lineAmount())))
                .toList();
    }

    private void applyDraft(Voucher voucher, VoucherDraft draft, Long currentId) {
        if (draft == null) {
            throw new IllegalArgumentException("Du lieu voucher khong hop le");
        }

        String code = normalizeCode(draft.code());
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code la bat buoc");
        }
        if (!StringUtils.hasText(draft.title())) {
            throw new IllegalArgumentException("title la bat buoc");
        }
        if (draft.discountType() == null) {
            throw new IllegalArgumentException("discountType la bat buoc");
        }
        if (draft.discountValue() == null || draft.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("discountValue phai > 0");
        }

        BigDecimal discountValue = normalizeAmount(draft.discountValue());
        if (draft.discountType() == VoucherDiscountType.PERCENT && discountValue.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("discountValue theo % khong duoc vuot qua 100");
        }

        BigDecimal minOrderAmount = draft.minOrderAmount() == null ? null : normalizeAmount(draft.minOrderAmount());
        BigDecimal maxDiscountAmount = draft.maxDiscountAmount() == null ? null : normalizeAmount(draft.maxDiscountAmount());
        if (minOrderAmount != null && minOrderAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("minOrderAmount khong hop le");
        }
        if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxDiscountAmount phai > 0");
        }

        Integer totalQuota = draft.totalQuota();
        Integer perUserLimit = draft.perUserLimit();
        Set<Long> eligibleProductIds = normalizeEligibleProductIds(draft.eligibleProductIds());
        if (totalQuota != null && totalQuota <= 0) {
            throw new IllegalArgumentException("totalQuota phai > 0");
        }
        if (perUserLimit != null && perUserLimit <= 0) {
            throw new IllegalArgumentException("perUserLimit phai > 0");
        }
        if (totalQuota != null && perUserLimit != null && perUserLimit > totalQuota) {
            throw new IllegalArgumentException("perUserLimit khong duoc lon hon totalQuota");
        }

        LocalDateTime startAt = draft.startAt();
        LocalDateTime endAt = draft.endAt();
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("endAt phai sau startAt");
        }

        if (currentId == null) {
            if (voucherRepository.existsByCodeIgnoreCase(code)) {
                throw new IllegalArgumentException("code voucher da ton tai");
            }
        } else if (voucherRepository.existsByCodeIgnoreCaseAndIdNot(code, currentId)) {
            throw new IllegalArgumentException("code voucher da ton tai");
        }

        voucher.setCode(code);
        voucher.setTitle(draft.title().trim());
        voucher.setDescription(StringUtils.hasText(draft.description()) ? draft.description().trim() : null);
        voucher.setDiscountType(draft.discountType());
        voucher.setDiscountValue(discountValue);
        voucher.setMinOrderAmount(minOrderAmount);
        voucher.setMaxDiscountAmount(maxDiscountAmount);
        voucher.setTotalQuota(totalQuota);
        voucher.setPerUserLimit(perUserLimit);
        voucher.setEligibleProductIds(eligibleProductIds);
        voucher.setStartAt(startAt);
        voucher.setEndAt(endAt);
        voucher.setActive(draft.active() == null ? Boolean.TRUE : draft.active());
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        return normalized;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return "unknown";
        }
        return username.trim();
    }

    private Set<Long> normalizeEligibleProductIds(Set<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<Long> ids = rawIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (ids.isEmpty()) {
            return new LinkedHashSet<>();
        }

        long existsCount = productRepository.findAllById(ids).stream().count();
        if (existsCount != ids.size()) {
            throw new IllegalArgumentException("Co san pham khong ton tai trong danh sach ap dung voucher");
        }
        return ids;
    }

    public record VoucherDraft(
            String code,
            String title,
            String description,
            VoucherDiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            Integer totalQuota,
            Integer perUserLimit,
            Set<Long> eligibleProductIds,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean active
    ) {
    }

    public record VoucherEvaluation(
            boolean valid,
            String message,
            Voucher voucher,
            BigDecimal orderAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            long usedCount,
            long userUsedCount
    ) {
    }

    public record AppliedVoucher(
            String code,
            String title,
            String discountType,
            BigDecimal orderAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount
    ) {
        public static AppliedVoucher none(BigDecimal orderAmount) {
            return new AppliedVoucher(null, null, null, orderAmount, ZERO, orderAmount);
        }
    }

    public record VoucherUsageStats(
            long usedCount,
            Integer remainingQuota
    ) {
    }

    public record OrderLine(
            Long productId,
            BigDecimal lineAmount
    ) {
    }
}

