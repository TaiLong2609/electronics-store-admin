package com.example.ecomerce.service;

import com.example.ecomerce.models.Order;
import com.example.ecomerce.models.Product;
import com.example.ecomerce.repository.ProductRepository;
import com.example.ecomerce.security.Permission;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class OrderService {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_FULFILLED = "FULFILLED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_RETURNED = "RETURNED";

    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final ProductRepository productRepository;
    private final VoucherService voucherService;
    private final TransactionService transactionService;

    public OrderService(
            ProductRepository productRepository,
            VoucherService voucherService,
            TransactionService transactionService
    ) {
        this.productRepository = productRepository;
        this.voucherService = voucherService;
        this.transactionService = transactionService;
    }

    @Transactional
    public Order createOrder(String username, Long productId, Integer quantity, String voucherCode) {
        if (productId == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("productId và quantity > 0 là bắt buộc");
        }

        Product product = requireProductForUpdate(productId);
        int available = safeAvailable(product);
        if (available < quantity) {
            throw new IllegalArgumentException("Không đủ tồn khả dụng. Hiện có=" + available + ", cần=" + quantity);
        }

        product.setReservedQuantity(safeReserved(product) + quantity);
        productRepository.save(product);

        String actor = StringUtils.hasText(username) ? username.trim() : "unknown";
        long orderId = nextId.getAndIncrement();
        BigDecimal unitPrice = toMoney(product.getPrice());
        BigDecimal subtotalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        VoucherService.AppliedVoucher appliedVoucher = voucherService.consumeForOrder(
                actor,
                voucherCode,
                subtotalAmount,
                orderId,
                List.of(new VoucherService.OrderLine(productId, subtotalAmount))
        );

        var order = new Order();
        order.setId(orderId);
        order.setUsername(actor);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setStatus(STATUS_CONFIRMED);
        order.setUnitPrice(unitPrice.doubleValue());
        order.setSubtotalAmount(subtotalAmount.doubleValue());
        order.setVoucherCode(appliedVoucher.code());
        order.setDiscountAmount(appliedVoucher.discountAmount().doubleValue());
        order.setPayableAmount(appliedVoucher.finalAmount().doubleValue());

        transactionService.recordOrderIncome(order, actor);
        orders.put(order.getId(), order);
        return order;
    }

    public List<Order> listOrders() {
        return Collections.unmodifiableList(
                orders.values().stream()
                        .sorted(Comparator.comparingLong(Order::getId))
                        .collect(Collectors.toList())
        );
    }

    public List<Order> listOrdersFor(Authentication authentication) {
        if (authentication == null) {
            return List.of();
        }

        var canViewAll = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Permission.ORDER_VIEW_ALL.getPermission()));

        if (canViewAll) {
            return listOrders();
        }
        return listOrdersForUser(authentication.getName());
    }

    public List<Order> listOrdersForUser(String username) {
        if (!StringUtils.hasText(username)) {
            return List.of();
        }

        return orders.values().stream()
                .filter(order -> username.equals(order.getUsername()))
                .sorted(Comparator.comparingLong(Order::getId))
                .collect(Collectors.toList());
    }

    public Order getOrder(Long id) {
        return orders.get(id);
    }

    @Transactional
    public Order updateStatus(Long id, String status, String actor) {
        Order order = orders.get(id);
        if (order == null) {
            return null;
        }

        String targetStatus = normalizeStatus(status);
        if (!STATUS_CANCELLED.equals(targetStatus) && !STATUS_CONFIRMED.equals(targetStatus)) {
            throw new IllegalArgumentException("Sales chỉ được phép chuyển trạng thái CONFIRMED hoặc CANCELLED");
        }

        synchronized (order) {
            String currentStatus = normalizeStatus(order.getStatus());
            if (targetStatus.equals(currentStatus)) {
                return order;
            }

            if (STATUS_CANCELLED.equals(targetStatus)) {
                if (isFinal(currentStatus)) {
                    throw new IllegalArgumentException("Đơn hàng đã hoàn tất, không thể hủy");
                }
                if (STATUS_CONFIRMED.equals(currentStatus)) {
                    releaseReservation(order.getProductId(), order.getQuantity());
                    voucherService.releaseForOrder(order.getId(), "ORDER_CANCELLED");
                    transactionService.recordOrderRefund(order, actor, "ORDER_CANCELLED");
                }
                order.setStatus(STATUS_CANCELLED);
                return order;
            }

            if (isFinal(currentStatus)) {
                throw new IllegalArgumentException("Đơn hàng đã hoàn tất, không thể xác nhận lại");
            }
            if (STATUS_CANCELLED.equals(currentStatus)) {
                throw new IllegalArgumentException("Đơn hàng đã hủy, hãy tạo đơn mới");
            }

            if (!STATUS_CONFIRMED.equals(currentStatus)) {
                reserveOrderQuantity(order.getProductId(), order.getQuantity());
                transactionService.recordOrderIncome(order, actor);
            }
            order.setStatus(STATUS_CONFIRMED);
            return order;
        }
    }

    public Order markAsShipped(Long id) {
        Order order = orders.get(id);
        if (order == null) {
            return null;
        }

        synchronized (order) {
            String status = normalizeStatus(order.getStatus());
            if (STATUS_CANCELLED.equals(status) || STATUS_RETURNED.equals(status)) {
                throw new IllegalArgumentException("Đơn hàng không còn ở trạng thái xuất kho");
            }
            if (!STATUS_CONFIRMED.equals(status)
                    && !STATUS_PENDING.equals(status)
                    && !STATUS_NEW.equals(status)
                    && !STATUS_SHIPPED.equals(status)
                    && !STATUS_FULFILLED.equals(status)) {
                throw new IllegalArgumentException("Không thể xuất kho ở trạng thái hiện tại: " + status);
            }
            order.setStatus(STATUS_SHIPPED);
            return order;
        }
    }

    public Order markAsReturned(Long id) {
        Order order = orders.get(id);
        if (order == null) {
            return null;
        }

        synchronized (order) {
            String status = normalizeStatus(order.getStatus());
            if (!STATUS_SHIPPED.equals(status) && !STATUS_FULFILLED.equals(status)) {
                throw new IllegalArgumentException("Chỉ đơn đã xuất kho mới được hoàn trả");
            }
            order.setStatus(STATUS_RETURNED);
            return order;
        }
    }

    private boolean isFinal(String status) {
        return STATUS_SHIPPED.equals(status)
                || STATUS_FULFILLED.equals(status)
                || STATUS_RETURNED.equals(status);
    }

    @Transactional
    protected void reserveOrderQuantity(Long productId, int quantity) {
        if (productId == null || quantity <= 0) {
            throw new IllegalArgumentException("productId và quantity > 0 là bắt buộc");
        }

        Product product = requireProductForUpdate(productId);
        int available = safeAvailable(product);
        if (available < quantity) {
            throw new IllegalArgumentException("Không đủ tồn khả dụng. Hiện có=" + available + ", cần=" + quantity);
        }
        product.setReservedQuantity(safeReserved(product) + quantity);
        productRepository.save(product);
    }

    @Transactional
    protected void releaseReservation(Long productId, int quantity) {
        if (productId == null || quantity <= 0) {
            return;
        }

        Product product = requireProductForUpdate(productId);
        int reserved = safeReserved(product);
        int released = Math.min(reserved, quantity);
        if (released <= 0) {
            return;
        }
        product.setReservedQuantity(reserved - released);
        productRepository.save(product);
    }

    private Product requireProductForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm: " + productId));
    }

    private int safeAvailable(Product product) {
        return Math.max(0, safeInventory(product) - safeReserved(product));
    }

    private int safeInventory(Product product) {
        return product.getInventoryQuantity() == null ? 0 : Math.max(0, product.getInventoryQuantity());
    }

    private int safeReserved(Product product) {
        return product.getReservedQuantity() == null ? 0 : Math.max(0, product.getReservedQuantity());
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }
        return status.trim().toUpperCase();
    }

    private BigDecimal toMoney(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
