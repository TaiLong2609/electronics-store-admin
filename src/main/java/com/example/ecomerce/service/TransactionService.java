package com.example.ecomerce.service;

import com.example.ecomerce.models.FinancialTransaction;
import com.example.ecomerce.models.Order;
import com.example.ecomerce.models.TransactionMethod;
import com.example.ecomerce.models.TransactionStatus;
import com.example.ecomerce.models.TransactionType;
import com.example.ecomerce.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class TransactionService {

    private final FinancialTransactionRepository transactionRepository;

    public TransactionService(FinancialTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public FinancialTransaction createManualTransaction(String actor, ManualTransactionDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Dữ liệu giao dịch không hợp lệ");
        }
        if (draft.type() == null) {
            throw new IllegalArgumentException("type là bắt buộc");
        }

        BigDecimal amount = normalizeAmount(draft.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount phải > 0");
        }

        FinancialTransaction tx = new FinancialTransaction();
        tx.setType(draft.type());
        tx.setAmount(amount);
        tx.setMethod(draft.method() == null ? TransactionMethod.OTHER : draft.method());
        tx.setStatus(draft.status() == null ? TransactionStatus.PENDING : draft.status());
        if (tx.getStatus() == TransactionStatus.REVERSED) {
            throw new IllegalArgumentException("Không thể tạo mới với trạng thái REVERSED");
        }
        if (draft.orderId() != null && draft.orderId() <= 0) {
            throw new IllegalArgumentException("orderId phải > 0 nếu được cung cấp");
        }
        tx.setOrderId(draft.orderId());
        tx.setReferenceCode(trimToNull(draft.referenceCode()));
        tx.setNote(trimToNull(draft.note()));
        tx.setCreatedBy(actorOrSystem(actor));
        tx.setUpdatedBy(actorOrSystem(actor));
        return transactionRepository.save(tx);
    }

    public FinancialTransaction confirmTransaction(Long transactionId, String actor) {
        FinancialTransaction tx = requireTransaction(transactionId);
        if (tx.getStatus() == TransactionStatus.SUCCESS) {
            return tx;
        }
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalArgumentException("Chỉ giao dịch PENDING mới được xác nhận");
        }
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setUpdatedBy(actorOrSystem(actor));
        return transactionRepository.save(tx);
    }

    public ReverseResult reverseTransaction(Long transactionId, String actor, String reason) {
        FinancialTransaction source = requireTransaction(transactionId);
        if (source.getStatus() != TransactionStatus.SUCCESS) {
            throw new IllegalArgumentException("Chỉ giao dịch SUCCESS mới được đảo");
        }
        if (transactionRepository.existsByRelatedTransactionId(source.getId())) {
            throw new IllegalArgumentException("Giao dịch này đã được đảo trước đó");
        }

        source.setStatus(TransactionStatus.REVERSED);
        source.setUpdatedBy(actorOrSystem(actor));
        source = transactionRepository.save(source);

        FinancialTransaction reversal = new FinancialTransaction();
        reversal.setType(reverseType(source.getType()));
        reversal.setAmount(source.getAmount());
        reversal.setMethod(source.getMethod());
        reversal.setStatus(TransactionStatus.SUCCESS);
        reversal.setOrderId(source.getOrderId());
        reversal.setRelatedTransactionId(source.getId());
        reversal.setReferenceCode("REV-" + source.getId());
        reversal.setNote(buildReverseNote(reason, source));
        reversal.setCreatedBy(actorOrSystem(actor));
        reversal.setUpdatedBy(actorOrSystem(actor));
        reversal = transactionRepository.save(reversal);

        return new ReverseResult(source, reversal);
    }

    @Transactional(readOnly = true)
    public List<FinancialTransaction> listTransactions(TransactionFilter filter) {
        validateFilter(filter);
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(tx -> matchesFilter(tx, filter))
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionSummary summarize(TransactionFilter filter) {
        List<FinancialTransaction> scoped = listTransactions(filter).stream()
                .filter(tx -> filter == null || filter.status() != null || tx.getStatus() == TransactionStatus.SUCCESS)
                .toList();

        BigDecimal income = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal expense = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (FinancialTransaction tx : scoped) {
            BigDecimal amount = normalizeAmount(tx.getAmount());
            if (tx.getType() == TransactionType.INCOME) {
                income = income.add(amount);
            } else {
                expense = expense.add(amount);
            }
        }
        return new TransactionSummary(
                income,
                expense,
                income.subtract(expense).setScale(2, RoundingMode.HALF_UP),
                scoped.size()
        );
    }

    public FinancialTransaction recordOrderIncome(Order order, String actor) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Thông tin đơn hàng không hợp lệ");
        }
        BigDecimal amount = normalizeAmount(order.getPayableAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return transactionRepository
                .findFirstByOrderIdAndTypeAndStatusOrderByIdAsc(order.getId(), TransactionType.INCOME, TransactionStatus.SUCCESS)
                .orElseGet(() -> {
                    FinancialTransaction tx = new FinancialTransaction();
                    tx.setType(TransactionType.INCOME);
                    tx.setAmount(amount);
                    tx.setMethod(TransactionMethod.ORDER);
                    tx.setStatus(TransactionStatus.SUCCESS);
                    tx.setOrderId(order.getId());
                    tx.setReferenceCode("ORDER-" + order.getId());
                    tx.setNote("Thu tiền đơn hàng #" + order.getId());
                    tx.setCreatedBy(actorOrSystem(actor));
                    tx.setUpdatedBy(actorOrSystem(actor));
                    return transactionRepository.save(tx);
                });
    }

    public FinancialTransaction recordOrderRefund(Order order, String actor, String reason) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Thông tin đơn hàng không hợp lệ");
        }
        BigDecimal amount = normalizeAmount(order.getPayableAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return transactionRepository
                .findFirstByOrderIdAndTypeAndStatusOrderByIdAsc(order.getId(), TransactionType.REFUND, TransactionStatus.SUCCESS)
                .orElseGet(() -> {
                    FinancialTransaction tx = new FinancialTransaction();
                    tx.setType(TransactionType.REFUND);
                    tx.setAmount(amount);
                    tx.setMethod(TransactionMethod.ORDER);
                    tx.setStatus(TransactionStatus.SUCCESS);
                    tx.setOrderId(order.getId());
                    tx.setReferenceCode("ORDER-REFUND-" + order.getId());
                    tx.setNote(buildRefundNote(reason, order.getId()));
                    tx.setCreatedBy(actorOrSystem(actor));
                    tx.setUpdatedBy(actorOrSystem(actor));
                    return transactionRepository.save(tx);
                });
    }

    private FinancialTransaction requireTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch: " + transactionId));
    }

    private void validateFilter(TransactionFilter filter) {
        if (filter == null) {
            return;
        }
        if (filter.fromAt() != null && filter.toAt() != null && filter.fromAt().isAfter(filter.toAt())) {
            throw new IllegalArgumentException("fromAt phải nhỏ hơn hoặc bằng toAt");
        }
    }

    private boolean matchesFilter(FinancialTransaction tx, TransactionFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.type() != null && tx.getType() != filter.type()) {
            return false;
        }
        if (filter.status() != null && tx.getStatus() != filter.status()) {
            return false;
        }
        if (filter.method() != null && tx.getMethod() != filter.method()) {
            return false;
        }
        if (filter.orderId() != null && !Objects.equals(tx.getOrderId(), filter.orderId())) {
            return false;
        }
        if (filter.fromAt() != null && (tx.getCreatedAt() == null || tx.getCreatedAt().isBefore(filter.fromAt()))) {
            return false;
        }
        if (filter.toAt() != null && (tx.getCreatedAt() == null || tx.getCreatedAt().isAfter(filter.toAt()))) {
            return false;
        }
        if (StringUtils.hasText(filter.keyword())) {
            String keyword = filter.keyword().trim().toLowerCase(Locale.ROOT);
            String haystack = String.join(" ",
                    String.valueOf(tx.getId()),
                    tx.getReferenceCode() == null ? "" : tx.getReferenceCode(),
                    tx.getNote() == null ? "" : tx.getNote(),
                    tx.getCreatedBy() == null ? "" : tx.getCreatedBy(),
                    tx.getUpdatedBy() == null ? "" : tx.getUpdatedBy(),
                    tx.getOrderId() == null ? "" : String.valueOf(tx.getOrderId())
            ).toLowerCase(Locale.ROOT);
            return haystack.contains(keyword);
        }
        return true;
    }

    private String actorOrSystem(String actor) {
        return StringUtils.hasText(actor) ? actor.trim() : "system";
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeAmount(Double amount) {
        if (amount == null || !Double.isFinite(amount)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private String buildReverseNote(String reason, FinancialTransaction source) {
        String base = "Đảo giao dịch #" + source.getId();
        if (!StringUtils.hasText(reason)) {
            return base;
        }
        return base + " - " + reason.trim();
    }

    private String buildRefundNote(String reason, Long orderId) {
        String base = "Hoàn tiền đơn hàng #" + orderId;
        if (!StringUtils.hasText(reason)) {
            return base;
        }
        return base + " - " + reason.trim();
    }

    private TransactionType reverseType(TransactionType source) {
        if (source == TransactionType.INCOME) {
            return TransactionType.REFUND;
        }
        return TransactionType.INCOME;
    }

    public record ManualTransactionDraft(
            TransactionType type,
            BigDecimal amount,
            TransactionMethod method,
            TransactionStatus status,
            Long orderId,
            String referenceCode,
            String note
    ) {
    }

    public record TransactionFilter(
            LocalDateTime fromAt,
            LocalDateTime toAt,
            TransactionType type,
            TransactionStatus status,
            TransactionMethod method,
            Long orderId,
            String keyword
    ) {
    }

    public record TransactionSummary(
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal netAmount,
            int totalTransactions
    ) {
    }

    public record ReverseResult(
            FinancialTransaction source,
            FinancialTransaction reversal
    ) {
    }
}

