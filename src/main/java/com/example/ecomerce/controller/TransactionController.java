package com.example.ecomerce.controller;

import com.example.ecomerce.models.FinancialTransaction;
import com.example.ecomerce.models.TransactionMethod;
import com.example.ecomerce.models.TransactionStatus;
import com.example.ecomerce.models.TransactionType;
import com.example.ecomerce.service.TransactionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
@PreAuthorize("hasAuthority('STATS_VIEW')")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<?> listTransactions(
            @RequestParam(name = "fromAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromAt,
            @RequestParam(name = "toAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toAt,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "method", required = false) String method,
            @RequestParam(name = "orderId", required = false) Long orderId,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        try {
            validateOrderId(orderId);
            TransactionService.TransactionFilter filter = new TransactionService.TransactionFilter(
                    fromAt,
                    toAt,
                    parseType(type),
                    parseStatus(status),
                    parseMethod(method),
                    orderId,
                    keyword
            );
            List<TransactionResponse> response = transactionService.listTransactions(filter).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestParam(name = "fromAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromAt,
            @RequestParam(name = "toAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toAt,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "method", required = false) String method,
            @RequestParam(name = "orderId", required = false) Long orderId,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        try {
            validateOrderId(orderId);
            TransactionService.TransactionFilter filter = new TransactionService.TransactionFilter(
                    fromAt,
                    toAt,
                    parseType(type),
                    parseStatus(status),
                    parseMethod(method),
                    orderId,
                    keyword
            );
            TransactionService.TransactionSummary summary = transactionService.summarize(filter);
            return ResponseEntity.ok(new TransactionSummaryResponse(
                    summary.totalIncome(),
                    summary.totalExpense(),
                    summary.netAmount(),
                    summary.totalTransactions()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createManualTransaction(
            Authentication authentication,
            @RequestBody CreateTransactionRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payload là bắt buộc"));
        }
        try {
            TransactionService.ManualTransactionDraft draft = new TransactionService.ManualTransactionDraft(
                    requireType(request.type()),
                    request.amount(),
                    parseMethod(request.method()),
                    parseStatus(request.status()),
                    request.orderId(),
                    request.referenceCode(),
                    request.note()
            );
            FinancialTransaction created = transactionService.createManualTransaction(actor(authentication), draft);
            return ResponseEntity.ok(toResponse(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{transactionId}/confirm")
    public ResponseEntity<?> confirmTransaction(
            Authentication authentication,
            @PathVariable Long transactionId
    ) {
        try {
            FinancialTransaction confirmed = transactionService.confirmTransaction(transactionId, actor(authentication));
            return ResponseEntity.ok(toResponse(confirmed));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{transactionId}/reverse")
    public ResponseEntity<?> reverseTransaction(
            Authentication authentication,
            @PathVariable Long transactionId,
            @RequestBody(required = false) ReverseTransactionRequest request
    ) {
        try {
            String reason = request == null ? null : request.reason();
            TransactionService.ReverseResult result = transactionService.reverseTransaction(
                    transactionId,
                    actor(authentication),
                    reason
            );
            return ResponseEntity.ok(new ReverseTransactionResponse(
                    toResponse(result.source()),
                    toResponse(result.reversal())
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private String actor(Authentication authentication) {
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return "system";
        }
        return authentication.getName().trim();
    }

    private void validateOrderId(Long orderId) {
        if (orderId != null && orderId <= 0) {
            throw new IllegalArgumentException("orderId phải > 0");
        }
    }

    private TransactionType requireType(String value) {
        TransactionType parsed = parseType(value);
        if (parsed == null) {
            throw new IllegalArgumentException("type là bắt buộc (INCOME|EXPENSE|REFUND)");
        }
        return parsed;
    }

    private TransactionType parseType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return TransactionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("type không hợp lệ. Hỗ trợ mã: INCOME, EXPENSE, REFUND");
        }
    }

    private TransactionStatus parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return TransactionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status không hợp lệ. Hỗ trợ mã: PENDING, SUCCESS, REVERSED");
        }
    }

    private TransactionMethod parseMethod(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return TransactionMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("method không hợp lệ. Hỗ trợ mã: CASH, BANK_TRANSFER, CARD, E_WALLET, ORDER, OTHER");
        }
    }

    private TransactionResponse toResponse(FinancialTransaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getType() == null ? null : tx.getType().name(),
                tx.getAmount(),
                tx.getMethod() == null ? null : tx.getMethod().name(),
                tx.getStatus() == null ? null : tx.getStatus().name(),
                tx.getOrderId(),
                tx.getRelatedTransactionId(),
                tx.getReferenceCode(),
                tx.getNote(),
                tx.getCreatedBy(),
                tx.getUpdatedBy(),
                tx.getCreatedAt() == null ? null : tx.getCreatedAt().toString(),
                tx.getUpdatedAt() == null ? null : tx.getUpdatedAt().toString()
        );
    }

    public record CreateTransactionRequest(
            String type,
            BigDecimal amount,
            String method,
            String status,
            Long orderId,
            String referenceCode,
            String note
    ) {
    }

    public record ReverseTransactionRequest(String reason) {
    }

    public record ReverseTransactionResponse(
            TransactionResponse source,
            TransactionResponse reversal
    ) {
    }

    public record TransactionResponse(
            Long id,
            String type,
            BigDecimal amount,
            String method,
            String status,
            Long orderId,
            Long relatedTransactionId,
            String referenceCode,
            String note,
            String createdBy,
            String updatedBy,
            String createdAt,
            String updatedAt
    ) {
    }

    public record TransactionSummaryResponse(
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal netAmount,
            int totalTransactions
    ) {
    }
}

