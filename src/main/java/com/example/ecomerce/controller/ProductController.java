package com.example.ecomerce.controller;

import com.example.ecomerce.models.AttributeDefinition;
import com.example.ecomerce.models.AttributeType;
import com.example.ecomerce.models.Category;
import com.example.ecomerce.models.CategoryAttribute;
import com.example.ecomerce.models.Product;
import com.example.ecomerce.models.ProductAttributeValue;
import com.example.ecomerce.repository.CategoryAttributeRepository;
import com.example.ecomerce.repository.CategoryRepository;
import com.example.ecomerce.repository.InventoryTransactionRepository;
import com.example.ecomerce.repository.ProductAttributeValueRepository;
import com.example.ecomerce.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductAttributeValueRepository productAttributeValueRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryAttributeRepository categoryAttributeRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "attributeKeyword", required = false) String attributeKeyword
    ) {
        String normalizedKeyword = normalize(keyword);
        String normalizedAttributeKeyword = normalize(attributeKeyword);

        List<Product> products = productRepository.findAllByOrderByIdAsc();
        Map<Long, List<ProductAttributeValue>> valuesByProductId = loadAttributeValueMap(products);

        return products.stream()
                .filter(product -> matchesKeyword(product, normalizedKeyword))
                .filter(product -> categoryId == null || matchesCategory(product, categoryId))
                .filter(product -> matchesAttributeKeyword(valuesByProductId.get(product.getId()), normalizedAttributeKeyword))
                .map(product -> toProductResponse(product, valuesByProductId.get(product.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy sản phẩm: " + id));
        }
        List<ProductAttributeValue> values = productAttributeValueRepository.findByProductIdOrderByIdAsc(id);
        return ResponseEntity.ok(toProductResponse(product, values));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    @Transactional
    public ResponseEntity<?> createProduct(@RequestBody ProductUpsertRequest request) {
        try {
            Product product = new Product();
            applyProductBasics(product, request);
            product = productRepository.save(product);
            syncProductAttributes(product, request.attributeValues());
            List<ProductAttributeValue> values = productAttributeValueRepository.findByProductIdOrderByIdAsc(product.getId());
            return ResponseEntity.ok(toProductResponse(product, values));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Transactional
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody ProductUpsertRequest request) {
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm: " + id));

            applyProductBasics(product, request);
            product = productRepository.save(product);
            syncProductAttributes(product, request.attributeValues());
            List<ProductAttributeValue> values = productAttributeValueRepository.findByProductIdOrderByIdAsc(product.getId());
            return ResponseEntity.ok(toProductResponse(product, values));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    @Transactional
    public String deleteProduct(@PathVariable Long id) {
        productAttributeValueRepository.deleteByProductId(id);
        inventoryTransactionRepository.deleteByProductId(id);
        productRepository.deleteById(id);
        return "Deleted successfully!";
    }

    @PostMapping("/backfill-metadata")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Transactional
    public ResponseEntity<?> backfillMetadata() {
        List<Product> products = productRepository.findAllByOrderByIdAsc();
        int locationsFilled = 0;
        int attributesFilled = 0;

        for (Product product : products) {
            if (product.getId() == null) {
                continue;
            }

            if (!StringUtils.hasText(product.getStorageLocation())) {
                product.setStorageLocation(defaultStorageLocation(product));
                productRepository.save(product);
                locationsFilled++;
            }

            if (product.getCategory() == null || product.getCategory().getId() == null) {
                continue;
            }

            List<CategoryAttribute> categoryAttributes =
                    categoryAttributeRepository.findByCategoryIdOrderBySortOrderAscIdAsc(product.getCategory().getId());
            for (CategoryAttribute categoryAttribute : categoryAttributes) {
                AttributeDefinition attribute = categoryAttribute.getAttribute();
                if (attribute == null || attribute.getId() == null) {
                    continue;
                }

                ProductAttributeValue value = productAttributeValueRepository
                        .findByProductIdAndAttributeId(product.getId(), attribute.getId())
                        .orElseGet(() -> {
                            ProductAttributeValue created = new ProductAttributeValue();
                            created.setProduct(product);
                            created.setAttribute(attribute);
                            return created;
                        });

                if (hasStoredValue(value)) {
                    continue;
                }

                fillDefaultAttributeValue(product, attribute, value);
                productAttributeValueRepository.save(value);
                attributesFilled++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "productsScanned", products.size(),
                "locationsFilled", locationsFilled,
                "attributesFilled", attributesFilled
        ));
    }

    @PostMapping("/bulk-import")
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    @Transactional
    public ResponseEntity<?> bulkImportProducts(@RequestBody BulkImportRequest request) {
        if (request == null || request.products() == null || request.products().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "products là bắt buộc và không được rỗng"));
        }

        Set<Long> categoryIds = request.products().stream()
                .filter(item -> item != null && item.categoryId() != null)
                .map(BulkProductItem::categoryId)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, Category> categoryById = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        if (categoryById.size() != categoryIds.size()) {
            Long missingCategoryId = categoryIds.stream()
                    .filter(id -> !categoryById.containsKey(id))
                    .findFirst()
                    .orElse(null);
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy categoryId: " + missingCategoryId));
        }

        List<Product> productsToSave = new ArrayList<>();
        for (int i = 0; i < request.products().size(); i++) {
            BulkProductItem item = request.products().get(i);
            int rowNumber = i + 1;
            if (item == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Dòng " + rowNumber + " không hợp lệ"));
            }

            String name = item.name() == null ? "" : item.name().trim();
            if (name.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Dòng " + rowNumber + ": name là bắt buộc"));
            }

            if (item.price() == null || item.price() <= 0 || Double.isNaN(item.price())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Dòng " + rowNumber + ": price phải > 0"));
            }

            int inventoryQuantity = item.inventoryQuantity() == null ? 0 : item.inventoryQuantity();
            if (inventoryQuantity < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Dòng " + rowNumber + ": inventoryQuantity phải >= 0"));
            }

            if (item.warrantyMonths() != null && item.warrantyMonths() < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Dòng " + rowNumber + ": warrantyMonths phải >= 0"));
            }

            int reorderLevel = item.reorderLevel() == null || item.reorderLevel() <= 0 ? 10 : item.reorderLevel();

            Product product = new Product();
            product.setName(name);
            product.setPrice(item.price());
            product.setDescription(item.description());
            product.setSku(trimToNull(item.sku()));
            product.setBarcode(trimToNull(item.barcode()));
            product.setWarrantyMonths(item.warrantyMonths());
            product.setInventoryQuantity(inventoryQuantity);
            product.setStorageLocation(trimToNull(item.storageLocation()));
            product.setReorderLevel(reorderLevel);
            product.setCategory(item.categoryId() == null ? null : categoryById.get(item.categoryId()));
            productsToSave.add(product);
        }

        boolean replaceExisting = Boolean.TRUE.equals(request.replaceExisting());
        if (replaceExisting) {
            productAttributeValueRepository.deleteAllInBatch();
            inventoryTransactionRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
        }

        List<Product> saved = productRepository.saveAll(productsToSave);
        return ResponseEntity.ok(Map.of(
                "imported", saved.size(),
                "replaceExisting", replaceExisting
        ));
    }

    private void applyProductBasics(Product product, ProductUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payload là bắt buộc");
        }

        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name là bắt buộc");
        }
        if (request.price() == null || request.price() <= 0 || Double.isNaN(request.price())) {
            throw new IllegalArgumentException("price phải > 0");
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy categoryId: " + request.categoryId()));
        }

        product.setName(name);
        product.setPrice(request.price());
        product.setDescription(trimToNull(request.description()));
        product.setSku(trimToNull(request.sku()));
        product.setMpn(trimToNull(request.mpn()));
        product.setBarcode(trimToNull(request.barcode()));
        product.setWarrantyMonths(request.warrantyMonths());
        product.setStorageLocation(trimToNull(request.storageLocation()));
        if (request.inventoryQuantity() != null) {
            product.setInventoryQuantity(Math.max(0, request.inventoryQuantity()));
        }
        if (request.reservedQuantity() != null) {
            product.setReservedQuantity(Math.max(0, request.reservedQuantity()));
        }
        if (request.reorderLevel() != null) {
            product.setReorderLevel(request.reorderLevel());
        }
        product.setCategory(category);
    }

    private void syncProductAttributes(Product product, List<ProductAttributeInput> requestedValues) {
        Long productId = product.getId();
        if (productId == null) {
            return;
        }

        Category category = product.getCategory();
        if (category == null) {
            productAttributeValueRepository.deleteByProductId(productId);
            return;
        }

        List<CategoryAttribute> categoryAttributes = categoryAttributeRepository.findByCategoryIdOrderBySortOrderAscIdAsc(category.getId());
        Map<Long, CategoryAttribute> allowedByAttributeId = categoryAttributes.stream()
                .filter(item -> item.getAttribute() != null && item.getAttribute().getId() != null)
                .collect(Collectors.toMap(item -> item.getAttribute().getId(), item -> item, (left, right) -> left, HashMap::new));

        List<ProductAttributeInput> incoming = requestedValues == null ? List.of() : requestedValues;
        Map<Long, ProductAttributeInput> incomingByAttributeId = new HashMap<>();
        for (ProductAttributeInput input : incoming) {
            if (input == null || input.attributeId() == null) {
                throw new IllegalArgumentException("attributeId là bắt buộc cho từng giá trị thuộc tính");
            }
            if (!allowedByAttributeId.containsKey(input.attributeId())) {
                throw new IllegalArgumentException("Thuộc tính không thuộc danh mục hiện tại: " + input.attributeId());
            }
            incomingByAttributeId.put(input.attributeId(), input);
        }

        for (CategoryAttribute categoryAttribute : categoryAttributes) {
            AttributeDefinition attribute = categoryAttribute.getAttribute();
            if (attribute == null || attribute.getId() == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(categoryAttribute.getRequired())) {
                continue;
            }
            ProductAttributeInput input = incomingByAttributeId.get(attribute.getId());
            if (!hasEffectiveValue(attribute.getType(), input)) {
                throw new IllegalArgumentException("Thiếu giá trị bắt buộc cho thuộc tính: " + attribute.getName());
            }
        }

        if (incomingByAttributeId.isEmpty()) {
            productAttributeValueRepository.deleteByProductId(productId);
            return;
        }

        List<Long> keepAttributeIds = new ArrayList<>(incomingByAttributeId.keySet());
        productAttributeValueRepository.deleteByProductIdAndAttributeIdNotIn(productId, keepAttributeIds);

        for (Map.Entry<Long, ProductAttributeInput> entry : incomingByAttributeId.entrySet()) {
            Long attributeId = entry.getKey();
            ProductAttributeInput input = entry.getValue();
            AttributeDefinition attribute = allowedByAttributeId.get(attributeId).getAttribute();
            ProductAttributeValue value = productAttributeValueRepository.findByProductIdAndAttributeId(productId, attributeId)
                    .orElseGet(ProductAttributeValue::new);
            value.setProduct(product);
            value.setAttribute(attribute);
            applyValue(value, attribute, input);
            productAttributeValueRepository.save(value);
        }
    }

    private void applyValue(ProductAttributeValue value, AttributeDefinition attribute, ProductAttributeInput input) {
        AttributeType type = attribute.getType();
        if (type == null) {
            type = AttributeType.STRING;
        }

        switch (type) {
            case NUMBER -> {
                Double number = input.valueNumber();
                if (number == null && StringUtils.hasText(input.valueText())) {
                    try {
                        number = Double.parseDouble(input.valueText().trim());
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Giá trị số không hợp lệ cho thuộc tính: " + attribute.getName());
                    }
                }
                value.setValueNumber(number);
                value.setValueText(null);
                value.setValueBoolean(null);
            }
            case BOOLEAN -> {
                Boolean bool = input.valueBoolean();
                if (bool == null && StringUtils.hasText(input.valueText())) {
                    String normalized = input.valueText().trim().toLowerCase(Locale.ROOT);
                    if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
                        bool = true;
                    } else if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
                        bool = false;
                    } else {
                        throw new IllegalArgumentException("Giá trị boolean không hợp lệ cho thuộc tính: " + attribute.getName());
                    }
                }
                value.setValueBoolean(bool);
                value.setValueText(null);
                value.setValueNumber(null);
            }
            default -> {
                String text = trimToNull(input.valueText());
                if (text == null && input.valueNumber() != null) {
                    text = String.valueOf(input.valueNumber());
                }
                if (text == null && input.valueBoolean() != null) {
                    text = String.valueOf(input.valueBoolean());
                }
                value.setValueText(text);
                value.setValueNumber(null);
                value.setValueBoolean(null);
            }
        }
    }

    private boolean hasEffectiveValue(AttributeType type, ProductAttributeInput input) {
        if (input == null) {
            return false;
        }
        AttributeType effectiveType = type == null ? AttributeType.STRING : type;
        return switch (effectiveType) {
            case NUMBER -> input.valueNumber() != null
                    || (StringUtils.hasText(input.valueText()) && isNumber(input.valueText()));
            case BOOLEAN -> input.valueBoolean() != null || StringUtils.hasText(input.valueText());
            case STRING -> StringUtils.hasText(input.valueText())
                    || input.valueNumber() != null
                    || input.valueBoolean() != null;
        };
    }

    private boolean isNumber(String value) {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean hasStoredValue(ProductAttributeValue value) {
        return StringUtils.hasText(value.getValueText())
                || value.getValueNumber() != null
                || value.getValueBoolean() != null;
    }

    private void fillDefaultAttributeValue(Product product, AttributeDefinition attribute, ProductAttributeValue value) {
        AttributeType type = attribute.getType() == null ? AttributeType.STRING : attribute.getType();
        switch (type) {
            case NUMBER -> {
                value.setValueNumber(0D);
                value.setValueText(null);
                value.setValueBoolean(null);
            }
            case BOOLEAN -> {
                value.setValueBoolean(false);
                value.setValueText(null);
                value.setValueNumber(null);
            }
            default -> {
                value.setValueText(defaultStringAttributeValue(product, attribute));
                value.setValueNumber(null);
                value.setValueBoolean(null);
            }
        }
    }

    private String defaultStringAttributeValue(Product product, AttributeDefinition attribute) {
        String code = attribute.getCode() == null ? "" : attribute.getCode().toUpperCase(Locale.ROOT);
        if (code.contains("SKU")) {
            return StringUtils.hasText(product.getSku()) ? product.getSku().trim() : "CHUA_CAP_NHAT";
        }
        if (code.contains("MPN")) {
            return StringUtils.hasText(product.getMpn()) ? product.getMpn().trim() : "CHUA_CAP_NHAT";
        }
        if (code.contains("MODEL")) {
            return StringUtils.hasText(product.getName()) ? product.getName().trim() : "CHUA_CAP_NHAT";
        }
        if (code.contains("BRAND")) {
            return detectBrand(product.getName());
        }
        return "CHUA_CAP_NHAT";
    }

    private String detectBrand(String productName) {
        if (!StringUtils.hasText(productName)) {
            return "CHUA_CAP_NHAT";
        }
        String[] parts = productName.trim().split("\\s+");
        return parts.length == 0 ? "CHUA_CAP_NHAT" : parts[0].toUpperCase(Locale.ROOT);
    }

    private String defaultStorageLocation(Product product) {
        String categorySegment = "GEN";
        if (product.getCategory() != null && StringUtils.hasText(product.getCategory().getName())) {
            String normalized = product.getCategory().getName()
                    .trim()
                    .toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]", "");
            if (normalized.length() >= 4) {
                categorySegment = normalized.substring(0, 4);
            } else if (!normalized.isEmpty()) {
                categorySegment = normalized;
            }
        }
        return "KHO-" + categorySegment + "-" + product.getId();
    }

    private Map<Long, List<ProductAttributeValue>> loadAttributeValueMap(List<Product> products) {
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

    private boolean matchesKeyword(Product product, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String key = keyword.toLowerCase(Locale.ROOT);
        return contains(product.getName(), key) || contains(product.getSku(), key) || contains(product.getMpn(), key);
    }

    private boolean matchesCategory(Product product, Long categoryId) {
        return product.getCategory() != null && categoryId.equals(product.getCategory().getId());
    }

    private boolean matchesAttributeKeyword(List<ProductAttributeValue> values, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        if (values == null || values.isEmpty()) {
            return false;
        }
        String key = keyword.toLowerCase(Locale.ROOT);
        for (ProductAttributeValue value : values) {
            String displayValue = displayValue(value).toLowerCase(Locale.ROOT);
            String attributeName = value.getAttribute() != null && value.getAttribute().getName() != null
                    ? value.getAttribute().getName().toLowerCase(Locale.ROOT)
                    : "";
            if (displayValue.contains(key) || attributeName.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private ProductResponse toProductResponse(Product product, List<ProductAttributeValue> values) {
        Long categoryId = null;
        String categoryName = null;
        if (product.getCategory() != null) {
            categoryId = product.getCategory().getId();
            categoryName = product.getCategory().getName();
        }

        List<ProductAttributeValueResponse> attributes = (values == null ? List.<ProductAttributeValue>of() : values).stream()
                .map(this::toAttributeValueResponse)
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getDescription(),
                product.getSku(),
                product.getMpn(),
                product.getBarcode(),
                product.getWarrantyMonths(),
                product.getInventoryQuantity(),
                product.getReservedQuantity(),
                product.getStorageLocation(),
                product.getReorderLevel(),
                categoryId,
                categoryName,
                attributes
        );
    }

    private ProductAttributeValueResponse toAttributeValueResponse(ProductAttributeValue value) {
        AttributeDefinition attribute = value.getAttribute();
        String type = attribute != null && attribute.getType() != null ? attribute.getType().name() : AttributeType.STRING.name();
        return new ProductAttributeValueResponse(
                attribute != null ? attribute.getId() : null,
                attribute != null ? attribute.getCode() : null,
                attribute != null ? attribute.getName() : null,
                type,
                attribute != null ? attribute.getUnit() : null,
                value.getValueText(),
                value.getValueNumber(),
                value.getValueBoolean(),
                displayValue(value)
        );
    }

    private String displayValue(ProductAttributeValue value) {
        if (value == null) {
            return "";
        }
        AttributeDefinition attribute = value.getAttribute();
        AttributeType type = attribute != null && attribute.getType() != null ? attribute.getType() : AttributeType.STRING;
        String unit = attribute != null ? trimToNull(attribute.getUnit()) : null;

        String raw = switch (type) {
            case NUMBER -> value.getValueNumber() == null ? "" : String.valueOf(value.getValueNumber());
            case BOOLEAN -> value.getValueBoolean() == null ? "" : (value.getValueBoolean() ? "true" : "false");
            case STRING -> value.getValueText() == null ? "" : value.getValueText();
        };
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return unit == null ? raw : raw + " " + unit;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ProductUpsertRequest(
            String name,
            Double price,
            String description,
            String sku,
            String mpn,
            String barcode,
            Integer warrantyMonths,
            Integer inventoryQuantity,
            Integer reservedQuantity,
            String storageLocation,
            Integer reorderLevel,
            Long categoryId,
            List<ProductAttributeInput> attributeValues
    ) {
    }

    public record ProductAttributeInput(
            Long attributeId,
            String valueText,
            Double valueNumber,
            Boolean valueBoolean
    ) {
    }

    public record ProductResponse(
            Long id,
            String name,
            Double price,
            String description,
            String sku,
            String mpn,
            String barcode,
            Integer warrantyMonths,
            Integer inventoryQuantity,
            Integer reservedQuantity,
            String storageLocation,
            Integer reorderLevel,
            Long categoryId,
            String categoryName,
            List<ProductAttributeValueResponse> attributeValues
    ) {
    }

    public record ProductAttributeValueResponse(
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

    public record BulkImportRequest(List<BulkProductItem> products, Boolean replaceExisting) {
    }

    public record BulkProductItem(
            String name,
            Double price,
            String description,
            String sku,
            String barcode,
            Integer warrantyMonths,
            Integer inventoryQuantity,
            String storageLocation,
            Integer reorderLevel,
            Long categoryId
    ) {
    }
}
