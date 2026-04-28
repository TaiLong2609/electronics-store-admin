package com.example.ecomerce.service;

import com.example.ecomerce.models.Category;
import com.example.ecomerce.models.InventoryTransaction;
import com.example.ecomerce.models.InventoryTransactionType;
import com.example.ecomerce.models.Order;
import com.example.ecomerce.models.Product;
import com.example.ecomerce.models.ProductAttributeValue;
import com.example.ecomerce.repository.CategoryRepository;
import com.example.ecomerce.repository.InventoryTransactionRepository;
import com.example.ecomerce.repository.ProductAttributeValueRepository;
import com.example.ecomerce.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final int DEFAULT_REORDER_LEVEL = 10;
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 20;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductAttributeValueRepository productAttributeValueRepository;
    private final OrderService orderService;
    private final InternalSkuService internalSkuService;
    private final int lowStockThreshold;

    public InventoryService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            InventoryTransactionRepository transactionRepository,
            ProductAttributeValueRepository productAttributeValueRepository,
            OrderService orderService,
            InternalSkuService internalSkuService,
            @Value("${app.inventory.low-stock-threshold:20}") Integer lowStockThreshold
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.productAttributeValueRepository = productAttributeValueRepository;
        this.orderService = orderService;
        this.internalSkuService = internalSkuService;
        this.lowStockThreshold = (lowStockThreshold != null && lowStockThreshold > 0)
                ? lowStockThreshold
                : DEFAULT_LOW_STOCK_THRESHOLD;
    }

    @Transactional(readOnly = true)
    public List<StockItemResponse> listStocks(String keyword, Long categoryId, String attributeKeyword) {
        List<Product> products;
        if (StringUtils.hasText(keyword)) {
            String key = keyword.trim();
            products = productRepository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCaseOrderByIdAsc(key, key);
        } else {
            products = productRepository.findAllByOrderByIdAsc();
        }

        Map<Long, List<ProductAttributeValue>> attributeValuesByProductId = loadProductAttributeValueMap(products);
        String normalizedAttributeKeyword = StringUtils.hasText(attributeKeyword)
                ? attributeKeyword.trim().toLowerCase(Locale.ROOT)
                : null;

        return products.stream()
                .filter(product -> categoryId == null || (product.getCategory() != null && categoryId.equals(product.getCategory().getId())))
                .filter(product -> matchesAttributeKeyword(attributeValuesByProductId.get(product.getId()), normalizedAttributeKeyword))
                .map(product -> toStockItem(product, attributeValuesByProductId.get(product.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryOptionResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(category -> new CategoryOptionResponse(
                        category.getId(),
                        category.getName(),
                        category.getParent() != null ? category.getParent().getId() : null,
                        category.getParent() != null ? category.getParent().getName() : null
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockItemResponse> listLowStock(Integer threshold) {
        int level = threshold != null && threshold > 0 ? threshold : lowStockThreshold;
        List<Product> products = productRepository.findAllByOrderByIdAsc();
        Map<Long, List<ProductAttributeValue>> attributeValuesByProductId = loadProductAttributeValueMap(products);

        return products.stream()
                .filter(product -> {
                    int qty = safeAvailable(product);
                    return qty <= level;
                })
                .map(product -> toStockItem(product, attributeValuesByProductId.get(product.getId())))
                .sorted(Comparator.comparingInt(StockItemResponse::availableQuantity))
                .toList();
    }

    @Transactional(readOnly = true)
    public StockReportResponse buildStockReport(
            LocalDate fromDate,
            LocalDate toDate,
            Long categoryId,
            String attributeKeyword,
            Boolean lowStockOnly
    ) {
        LocalDateTime fromAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toAt = toDate == null ? null : toDate.atTime(LocalTime.MAX);
        if (fromAt != null && toAt != null && toAt.isBefore(fromAt)) {
            throw new IllegalArgumentException("toDate phải lớn hơn hoặc bằng fromDate");
        }

        List<StockItemResponse> stocks = listStocks(null, categoryId, attributeKeyword).stream()
                .filter(item -> !Boolean.TRUE.equals(lowStockOnly) || Boolean.TRUE.equals(item.lowStock()))
                .toList();

        if (stocks.isEmpty()) {
            return new StockReportResponse(List.of(), 0, 0, 0, fromDate, toDate);
        }

        Map<Long, StockItemResponse> stockByProductId = stocks.stream()
                .filter(item -> item.productId() != null)
                .collect(Collectors.toMap(StockItemResponse::productId, Function.identity(), (left, right) -> left, HashMap::new));

        Map<Long, Integer> openingByProductId = new HashMap<>();
        if (fromAt != null) {
            for (InventoryTransaction tx : transactionRepository.findByCreatedAtBeforeOrderByCreatedAtDesc(fromAt)) {
                Long productId = tx.getProduct() != null ? tx.getProduct().getId() : null;
                if (productId == null || !stockByProductId.containsKey(productId) || openingByProductId.containsKey(productId)) {
                    continue;
                }
                openingByProductId.put(productId, tx.getBalanceAfter() == null ? 0 : tx.getBalanceAfter());
            }
        }

        List<InventoryTransaction> periodTransactions;
        if (fromAt != null && toAt != null) {
            periodTransactions = transactionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(fromAt, toAt);
        } else if (fromAt != null) {
            periodTransactions = transactionRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(fromAt);
        } else if (toAt != null) {
            periodTransactions = transactionRepository.findByCreatedAtLessThanEqualOrderByCreatedAtAsc(toAt);
        } else {
            periodTransactions = transactionRepository.findAllByOrderByCreatedAtAsc();
        }

        Map<Long, StockReportAccumulator> accumulatorByProductId = new HashMap<>();
        for (InventoryTransaction tx : periodTransactions) {
            Long productId = tx.getProduct() != null ? tx.getProduct().getId() : null;
            if (productId == null || !stockByProductId.containsKey(productId)) {
                continue;
            }
            StockReportAccumulator acc = accumulatorByProductId.computeIfAbsent(productId, key -> new StockReportAccumulator());
            int change = tx.getQuantityChange() == null ? 0 : tx.getQuantityChange();
            acc.netQuantity += change;
            if (change >= 0) {
                acc.inboundQuantity += change;
            } else {
                acc.outboundQuantity += -change;
            }
            if (tx.getCreatedAt() != null) {
                acc.lastTransactionAt = tx.getCreatedAt();
            }
        }

        List<StockReportRowResponse> rows = new ArrayList<>();
        int totalInbound = 0;
        int totalOutbound = 0;
        int totalNet = 0;

        for (StockItemResponse stock : stocks) {
            Long productId = stock.productId();
            if (productId == null) {
                continue;
            }
            int opening = openingByProductId.getOrDefault(productId, 0);
            StockReportAccumulator acc = accumulatorByProductId.getOrDefault(productId, new StockReportAccumulator());
            int inbound = acc.inboundQuantity;
            int outbound = acc.outboundQuantity;
            int net = acc.netQuantity;
            int closing = opening + net;
            totalInbound += inbound;
            totalOutbound += outbound;
            totalNet += net;

            rows.add(new StockReportRowResponse(
                    productId,
                    stock.sku(),
                    stock.name(),
                    stock.categoryName(),
                    opening,
                    inbound,
                    outbound,
                    net,
                    closing,
                    stock.inventoryQuantity(),
                    stock.availableQuantity(),
                    stock.reorderLevel(),
                    stock.lowStock(),
                    acc.lastTransactionAt == null ? null : acc.lastTransactionAt.toString(),
                    stock.attributeValues()
            ));
        }

        rows.sort(Comparator
                .comparing(StockReportRowResponse::sku, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(StockReportRowResponse::name, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(StockReportRowResponse::productId, Comparator.nullsLast(Long::compareTo)));

        return new StockReportResponse(rows, totalInbound, totalOutbound, totalNet, fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public List<OrderFulfillmentItemResponse> listOrdersForFulfillment() {
        List<Order> orders = orderService.listOrders().stream()
                .filter(order -> {
                    String status = normalizeStatus(order.getStatus());
                    return OrderService.STATUS_CONFIRMED.equals(status);
                })
                .sorted(Comparator.comparingLong(Order::getId))
                .toList();

        List<Long> productIds = orders.stream()
                .map(Order::getProductId)
                .distinct()
                .toList();

        Map<Long, Product> byId = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return orders.stream()
                .map(order -> {
                    Product product = byId.get(order.getProductId());
                    return new OrderFulfillmentItemResponse(
                            order.getId(),
                            order.getUsername(),
                            order.getProductId(),
                            product != null ? product.getName() : "Không xác định",
                            product != null ? product.getSku() : "",
                            order.getQuantity(),
                            order.getStatus(),
                            product != null ? safeAvailable(product) : 0
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryLedgerEntryResponse> getLedgerBySku(String skuOrMpn) {
        Product product = requireProductBySkuOrMpn(skuOrMpn);
        Long productId = product.getId();

        return transactionRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(tx -> new InventoryLedgerEntryResponse(
                        tx.getId(),
                        tx.getProduct().getId(),
                        tx.getTransactionType().name(),
                        tx.getQuantityChange(),
                        tx.getBalanceAfter(),
                        tx.getReferenceCode(),
                        tx.getNote(),
                        tx.getCreatedBy(),
                    tx.getCreatedAt() == null ? null : tx.getCreatedAt().toString()
                ))
                .toList();
    }

    @Transactional
    public MutationResponse receiveInbound(String actor, InboundRequest request) {
        if (request == null || !StringUtils.hasText(request.sku()) || request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("sku và quantity > 0 là bắt buộc");
        }

        Product product = requireProductForUpdateBySkuOrMpn(request.sku());
        int newBalance = safeInventory(product) + request.quantity();

        product.setInventoryQuantity(newBalance);
        if (StringUtils.hasText(request.location())) {
            product.setStorageLocation(request.location().trim());
        }
        product = productRepository.save(product);

        logTransaction(
                product,
                InventoryTransactionType.INBOUND,
                request.quantity(),
                newBalance,
                request.referenceCode(),
                request.note(),
                actor
        );

        return new MutationResponse("Đã tạo phiếu nhập kho", toStockItem(product, null));
    }

    @Transactional
    public MutationResponse manualAddProduct(String actor, ManualAddRequest request) {
        if (request == null || !StringUtils.hasText(request.name()) || !StringUtils.hasText(request.mpn())) {
            throw new IllegalArgumentException("name và mpn là bắt buộc");
        }
        if (request.categoryId() == null) {
            throw new IllegalArgumentException("categoryId là bắt buộc");
        }

        String normalizedMpn = request.mpn().trim();
        if (productRepository.findFirstByMpnIgnoreCase(normalizedMpn).isPresent()
                || productRepository.findFirstBySkuIgnoreCase(normalizedMpn).isPresent()) {
            throw new IllegalArgumentException("Mã linh kiện (MPN) đã tồn tại: " + normalizedMpn);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục: " + request.categoryId()));

        int initialQuantity = request.initialQuantity() == null ? 0 : request.initialQuantity();
        if (initialQuantity < 0) {
            throw new IllegalArgumentException("initialQuantity phải >= 0");
        }

        int reorder = request.reorderLevel() == null || request.reorderLevel() <= 0
                ? DEFAULT_REORDER_LEVEL
                : request.reorderLevel();

        Product product = new Product();
        product.setName(request.name().trim());
        product.setMpn(normalizedMpn);
        product.setSku(null);
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setBarcode(request.barcode());
        product.setWarrantyMonths(request.warrantyMonths());
        product.setInventoryQuantity(initialQuantity);
        product.setReservedQuantity(0);
        product.setStorageLocation(StringUtils.hasText(request.storageLocation()) ? request.storageLocation().trim() : null);
        product.setReorderLevel(reorder);
        product.setCategory(category);

        product = productRepository.save(product);
        product = internalSkuService.ensureSku(product);

        if (initialQuantity > 0) {
            logTransaction(
                    product,
                    InventoryTransactionType.MANUAL_ADD,
                    initialQuantity,
                    initialQuantity,
                    "MANUAL",
                    "Thêm linh kiện thủ công",
                    actor
            );
        }

        return new MutationResponse("Đã thêm linh kiện thủ công", toStockItem(product, null));
    }

    @Transactional
    public MutationResponse updateLocation(String actor, Long productId, LocationUpdateRequest request) {
        if (productId == null || request == null || !StringUtils.hasText(request.storageLocation())) {
            throw new IllegalArgumentException("productId và storageLocation là bắt buộc");
        }

        Product product = requireProductForUpdate(productId);
        product.setStorageLocation(request.storageLocation().trim());
        product = productRepository.save(product);

        logTransaction(
                product,
                InventoryTransactionType.LOCATION_UPDATE,
                0,
                safeInventory(product),
                "LOCATION",
                StringUtils.hasText(request.note())
                        ? request.note().trim()
                    : "Cập nhật vị trí thành " + request.storageLocation().trim(),
                actor
        );

            return new MutationResponse("Đã cập nhật vị trí lưu trữ", toStockItem(product, null));
    }

    @Transactional
    public OutboundResponse fulfillOrder(String actor, OutboundRequest request) {
        if (request == null || request.orderId() == null) {
            throw new IllegalArgumentException("orderId là bắt buộc");
        }

        Order order = requireOrder(request.orderId());
        synchronized (order) {
            String status = normalizeStatus(order.getStatus());
            if (OrderService.STATUS_SHIPPED.equals(status) || OrderService.STATUS_FULFILLED.equals(status)) {
                throw new IllegalArgumentException("Đơn hàng đã được xuất kho trước đó");
            }
            if (OrderService.STATUS_CANCELLED.equals(status) || OrderService.STATUS_RETURNED.equals(status)) {
                throw new IllegalArgumentException("Đơn hàng không còn ở trạng thái xuất kho");
            }
            if (!OrderService.STATUS_CONFIRMED.equals(status)) {
                throw new IllegalArgumentException("Chỉ đơn hàng ở trạng thái CONFIRMED mới được xuất kho");
            }
            if (order.getQuantity() == null || order.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng đơn hàng không hợp lệ");
            }

            Product product = requireProductForUpdate(order.getProductId());
            int stock = safeInventory(product);
            int reserved = safeReserved(product);
            int quantity = order.getQuantity();
            int reservedForOrder = Math.min(reserved, quantity);
            int available = safeAvailable(product);

            if (stock < quantity) {
                throw new IllegalArgumentException("Không đủ tồn kho thực tế. Hiện có=" + stock + ", cần=" + quantity);
            }
            if (reservedForOrder < quantity && available < quantity) {
                throw new IllegalArgumentException("Không đủ tồn khả dụng. Hiện có=" + available + ", cần=" + quantity);
            }

            int newStock = stock - quantity;
            int newReserved = Math.max(0, reserved - reservedForOrder);
            product.setInventoryQuantity(newStock);
            product.setReservedQuantity(newReserved);
            product = productRepository.save(product);

            order = orderService.markAsShipped(order.getId());

            logTransaction(
                    product,
                    InventoryTransactionType.OUTBOUND,
                    -quantity,
                    newStock,
                    "ORDER-" + order.getId(),
                    request.note(),
                    actor
            );

            return new OutboundResponse(
                    "Đã xuất kho đơn hàng và trừ tồn",
                    toStockItem(product, null),
                    toOrderFulfillmentResponse(order, product)
            );
        }
    }

    @Transactional
    public OutboundResponse processReturn(String actor, ReturnRequest request) {
        if (request == null || request.orderId() == null) {
            throw new IllegalArgumentException("orderId là bắt buộc");
        }

        Order order = requireOrder(request.orderId());
        synchronized (order) {
            int returnQuantity = request.quantity() == null ? defaultOrderQuantity(order) : request.quantity();
            if (returnQuantity <= 0) {
                throw new IllegalArgumentException("Số lượng hoàn trả phải > 0");
            }
            if (returnQuantity > defaultOrderQuantity(order)) {
                throw new IllegalArgumentException("Số lượng hoàn trả vượt quá số lượng của đơn");
            }
            String status = normalizeStatus(order.getStatus());
            if (!OrderService.STATUS_SHIPPED.equals(status) && !OrderService.STATUS_FULFILLED.equals(status)) {
                throw new IllegalArgumentException("Chỉ đơn đã xuất kho mới được hoàn trả");
            }

            Product product = requireProductForUpdate(order.getProductId());
            int newBalance = safeInventory(product) + returnQuantity;
            product.setInventoryQuantity(newBalance);
            product = productRepository.save(product);

            order = orderService.markAsReturned(order.getId());

            logTransaction(
                    product,
                    InventoryTransactionType.RETURN,
                    returnQuantity,
                    newBalance,
                    "RETURN-" + order.getId(),
                    request.note(),
                    actor
            );

            return new OutboundResponse(
                    "Đã xử lý hoàn trả và cộng lại tồn kho",
                    toStockItem(product, null),
                    toOrderFulfillmentResponse(order, product)
            );
        }
    }

    @Transactional
    public MutationResponse adjustStock(String actor, AdjustmentRequest request) {
        if (request == null || request.productId() == null || request.deltaQuantity() == null || request.deltaQuantity() == 0) {
            throw new IllegalArgumentException("productId và deltaQuantity (khác 0) là bắt buộc");
        }

        Product product = requireProductForUpdate(request.productId());
        int newBalance = safeInventory(product) + request.deltaQuantity();
        if (newBalance < 0) {
            throw new IllegalArgumentException("Điều chỉnh sẽ làm tồn kho âm");
        }
        if (newBalance < safeReserved(product)) {
            throw new IllegalArgumentException("Không thể giảm dưới số lượng đang được giữ chỗ");
        }

        product.setInventoryQuantity(newBalance);
        product = productRepository.save(product);

        logTransaction(
                product,
                InventoryTransactionType.ADJUSTMENT,
                request.deltaQuantity(),
                newBalance,
                "ADJUSTMENT",
                request.note(),
                actor
        );

        return new MutationResponse("Đã điều chỉnh tồn kho", toStockItem(product, null));
    }

    private InventoryTransaction logTransaction(
            Product product,
            InventoryTransactionType type,
            int quantityChange,
            int balanceAfter,
            String referenceCode,
            String note,
            String actor
    ) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setProduct(product);
        tx.setTransactionType(type);
        tx.setQuantityChange(quantityChange);
        tx.setBalanceAfter(balanceAfter);
        tx.setReferenceCode(referenceCode);
        tx.setNote(note);
        tx.setCreatedBy(StringUtils.hasText(actor) ? actor : "system");
        return transactionRepository.save(tx);
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm: " + productId));
    }

    private Product requireProductBySkuOrMpn(String skuOrMpn) {
        String normalized = skuOrMpn == null ? "" : skuOrMpn.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("sku là bắt buộc");
        }

        return productRepository.findFirstBySkuIgnoreCase(normalized)
                .or(() -> productRepository.findFirstByMpnIgnoreCase(normalized))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm theo SKU/MPN: " + normalized));
    }

    private Product requireProductForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm: " + productId));
    }

    private Product requireProductForUpdateBySkuOrMpn(String skuOrMpn) {
        String normalized = skuOrMpn == null ? "" : skuOrMpn.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("sku là bắt buộc");
        }

        Product product = productRepository.findFirstBySkuIgnoreCase(normalized)
                .or(() -> productRepository.findFirstByMpnIgnoreCase(normalized))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm theo SKU/MPN: " + normalized));

        return requireProductForUpdate(product.getId());
    }

    private Order requireOrder(Long orderId) {
        Order order = orderService.getOrder(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng: " + orderId);
        }
        return order;
    }

    private int defaultOrderQuantity(Order order) {
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            throw new IllegalArgumentException("Số lượng đơn hàng không hợp lệ");
        }
        return order.getQuantity();
    }

    private int safeInventory(Product product) {
        return product.getInventoryQuantity() == null ? 0 : Math.max(0, product.getInventoryQuantity());
    }

    private int safeReserved(Product product) {
        return product.getReservedQuantity() == null ? 0 : Math.max(0, product.getReservedQuantity());
    }

    private int safeAvailable(Product product) {
        return Math.max(0, safeInventory(product) - safeReserved(product));
    }

    private int safeReorderLevel(Product product) {
        return product.getReorderLevel() == null || product.getReorderLevel() <= 0
                ? DEFAULT_REORDER_LEVEL
                : product.getReorderLevel();
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private StockItemResponse toStockItem(Product product, List<ProductAttributeValue> attributeValues) {
        int physical = safeInventory(product);
        int reserved = safeReserved(product);
        int available = safeAvailable(product);
        int reorderLevel = safeReorderLevel(product);
        boolean lowStock = available <= lowStockThreshold;

        Long categoryId = null;
        String categoryName = null;
        if (product.getCategory() != null) {
            categoryId = product.getCategory().getId();
            categoryName = product.getCategory().getName();
        }

        List<AttributeValueResponse> attributes = (attributeValues == null ? List.<ProductAttributeValue>of() : attributeValues).stream()
                .map(this::toAttributeValueResponse)
                .toList();

        return new StockItemResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                physical,
                reserved,
                available,
                reorderLevel,
                lowStock,
                product.getStorageLocation(),
                categoryId,
                categoryName,
                product.getPrice(),
                attributes
        );
    }

    private Map<Long, List<ProductAttributeValue>> loadProductAttributeValueMap(List<Product> products) {
        Map<Long, List<ProductAttributeValue>> byProductId = new HashMap<>();
        if (products == null || products.isEmpty()) {
            return byProductId;
        }

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .filter(id -> id != null)
                .toList();
        if (productIds.isEmpty()) {
            return byProductId;
        }

        for (ProductAttributeValue value : productAttributeValueRepository.findByProductIdInOrderByProductIdAscIdAsc(productIds)) {
            if (value.getProduct() == null || value.getProduct().getId() == null) {
                continue;
            }
            byProductId.computeIfAbsent(value.getProduct().getId(), key -> new ArrayList<>()).add(value);
        }
        return byProductId;
    }

    private boolean matchesAttributeKeyword(List<ProductAttributeValue> values, String normalizedKeyword) {
        if (!StringUtils.hasText(normalizedKeyword)) {
            return true;
        }
        if (values == null || values.isEmpty()) {
            return false;
        }

        for (ProductAttributeValue value : values) {
            AttributeValueResponse response = toAttributeValueResponse(value);
            String display = response.displayValue() == null ? "" : response.displayValue().toLowerCase(Locale.ROOT);
            String name = response.name() == null ? "" : response.name().toLowerCase(Locale.ROOT);
            if (display.contains(normalizedKeyword) || name.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private AttributeValueResponse toAttributeValueResponse(ProductAttributeValue value) {
        String type = value.getAttribute() != null && value.getAttribute().getType() != null
                ? value.getAttribute().getType().name()
                : "STRING";
        String unit = value.getAttribute() != null ? value.getAttribute().getUnit() : null;

        String display = switch (type) {
            case "NUMBER" -> value.getValueNumber() == null ? "" : String.valueOf(value.getValueNumber());
            case "BOOLEAN" -> value.getValueBoolean() == null ? "" : (value.getValueBoolean() ? "true" : "false");
            default -> value.getValueText() == null ? "" : value.getValueText();
        };
        if (StringUtils.hasText(unit) && StringUtils.hasText(display)) {
            display = display + " " + unit.trim();
        }

        return new AttributeValueResponse(
                value.getAttribute() != null ? value.getAttribute().getId() : null,
                value.getAttribute() != null ? value.getAttribute().getCode() : null,
                value.getAttribute() != null ? value.getAttribute().getName() : null,
                type,
                unit,
                value.getValueText(),
                value.getValueNumber(),
                value.getValueBoolean(),
                display
        );
    }

    private OrderFulfillmentItemResponse toOrderFulfillmentResponse(Order order, Product product) {
        return new OrderFulfillmentItemResponse(
                order.getId(),
                order.getUsername(),
                order.getProductId(),
                product != null ? product.getName() : "",
                product != null ? product.getSku() : "",
                order.getQuantity(),
                order.getStatus(),
                product != null ? safeAvailable(product) : 0
        );
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

    public record MutationResponse(String message, StockItemResponse stock) {
    }

    public record OutboundResponse(String message, StockItemResponse stock, OrderFulfillmentItemResponse order) {
    }

    public record StockItemResponse(
            Long productId,
            String sku,
            String name,
            Integer inventoryQuantity,
            Integer reservedQuantity,
            Integer availableQuantity,
            Integer reorderLevel,
            Boolean lowStock,
            String storageLocation,
            Long categoryId,
            String categoryName,
            Double price,
            List<AttributeValueResponse> attributeValues
    ) {
    }

    public record AttributeValueResponse(
            Long attributeId,
            String code,
            String name,
            String type,
            String unit,
            String valueText,
            Double valueNumber,
            Boolean valueBoolean,
            String displayValue
    ) {
    }

    public record OrderFulfillmentItemResponse(
            Long orderId,
            String username,
            Long productId,
            String productName,
            String sku,
            Integer orderQuantity,
            String status,
            Integer availableQuantity
    ) {
    }

    public record InventoryLedgerEntryResponse(
            Long transactionId,
            Long productId,
            String transactionType,
            Integer quantityChange,
            Integer balanceAfter,
            String referenceCode,
            String note,
            String createdBy,
            String createdAt
    ) {
    }

    public record CategoryOptionResponse(Long id, String name, Long parentId, String parentName) {
    }

    public record StockReportRowResponse(
            Long productId,
            String sku,
            String name,
            String categoryName,
            Integer openingBalance,
            Integer inboundQuantity,
            Integer outboundQuantity,
            Integer netQuantity,
            Integer closingBalance,
            Integer currentInventory,
            Integer currentAvailable,
            Integer reorderLevel,
            Boolean lowStock,
            String lastTransactionAt,
            List<AttributeValueResponse> attributeValues
    ) {
    }

    public record StockReportResponse(
            List<StockReportRowResponse> rows,
            Integer totalInbound,
            Integer totalOutbound,
            Integer totalNet,
            LocalDate fromDate,
            LocalDate toDate
    ) {
    }

    private static final class StockReportAccumulator {
        private int inboundQuantity;
        private int outboundQuantity;
        private int netQuantity;
        private LocalDateTime lastTransactionAt;
    }
}
