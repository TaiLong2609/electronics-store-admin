package com.example.ecomerce.controller;

import com.example.ecomerce.models.AttributeDefinition;
import com.example.ecomerce.models.AttributeType;
import com.example.ecomerce.models.Category;
import com.example.ecomerce.models.CategoryAttribute;
import com.example.ecomerce.models.Product;
import com.example.ecomerce.repository.AttributeDefinitionRepository;
import com.example.ecomerce.repository.CategoryAttributeRepository;
import com.example.ecomerce.repository.CategoryRepository;
import com.example.ecomerce.repository.ProductAttributeValueRepository;
import com.example.ecomerce.repository.ProductRepository;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/categories")
@PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final AttributeDefinitionRepository attributeDefinitionRepository;
    private final ProductAttributeValueRepository productAttributeValueRepository;

    public CategoryController(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            CategoryAttributeRepository categoryAttributeRepository,
            AttributeDefinitionRepository attributeDefinitionRepository,
            ProductAttributeValueRepository productAttributeValueRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryAttributeRepository = categoryAttributeRepository;
        this.attributeDefinitionRepository = attributeDefinitionRepository;
        this.productAttributeValueRepository = productAttributeValueRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return buildCategoryResponses();
    }

    @GetMapping("/{categoryId}/attributes")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getCategoryAttributes(@PathVariable Long categoryId) {
        try {
            requireCategory(categoryId);
            List<CategoryAttributeResponse> response = categoryAttributeRepository.findByCategoryIdOrderBySortOrderAscIdAsc(categoryId)
                    .stream()
                    .map(this::toCategoryAttributeResponse)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{categoryId}/attributes")
    @Transactional
    public ResponseEntity<?> createCategoryAttribute(
            @PathVariable Long categoryId,
            @RequestBody CategoryAttributeUpsertRequest request
    ) {
        try {
            Category category = requireCategory(categoryId);
            if (request == null) {
                throw new IllegalArgumentException("Payload là bắt buộc");
            }

            AttributeDefinition attribute = resolveAttributeDefinitionForCreate(category, request);
            if (categoryAttributeRepository.existsByCategoryIdAndAttributeId(categoryId, attribute.getId())) {
                throw new IllegalArgumentException("Thuộc tính đã tồn tại trong danh mục này");
            }

            int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
            CategoryAttribute created = new CategoryAttribute(
                    null,
                    category,
                    attribute,
                    Boolean.TRUE.equals(request.required()),
                    sortOrder
            );
            created = categoryAttributeRepository.save(created);
            return ResponseEntity.ok(toCategoryAttributeResponse(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{categoryId}/attributes/{categoryAttributeId}")
    @Transactional
    public ResponseEntity<?> updateCategoryAttribute(
            @PathVariable Long categoryId,
            @PathVariable Long categoryAttributeId,
            @RequestBody CategoryAttributeUpsertRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Payload là bắt buộc");
            }

            CategoryAttribute mapping = categoryAttributeRepository.findByCategoryIdAndId(categoryId, categoryAttributeId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thuộc tính danh mục: " + categoryAttributeId));

            AttributeDefinition originalAttribute = mapping.getAttribute();
            AttributeDefinition targetAttribute = originalAttribute;

            if (request.attributeId() != null && !request.attributeId().equals(originalAttribute.getId())) {
                targetAttribute = attributeDefinitionRepository.findById(request.attributeId())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy định nghĩa thuộc tính: " + request.attributeId()));
                if (categoryAttributeRepository.existsByCategoryIdAndAttributeIdAndIdNot(categoryId, targetAttribute.getId(), categoryAttributeId)) {
                    throw new IllegalArgumentException("Thuộc tính đã tồn tại trong danh mục này");
                }
            }

            if (hasAttributeDefinitionPayload(request)) {
                if (targetAttribute != null && targetAttribute.getId() != null
                        && categoryAttributeRepository.countByAttributeId(targetAttribute.getId()) > 1) {
                    targetAttribute = cloneAttributeDefinition(targetAttribute);
                }
                targetAttribute = upsertAttributeDefinition(targetAttribute, request, requireCategory(categoryId));
                if (!targetAttribute.getId().equals(originalAttribute.getId())
                        && categoryAttributeRepository.existsByCategoryIdAndAttributeIdAndIdNot(categoryId, targetAttribute.getId(), categoryAttributeId)) {
                    throw new IllegalArgumentException("Thuộc tính đã tồn tại trong danh mục này");
                }
            }

            if (!targetAttribute.getId().equals(originalAttribute.getId())) {
                productAttributeValueRepository.deleteByProductCategoryAndAttribute(categoryId, originalAttribute.getId());
            }

            mapping.setAttribute(targetAttribute);
            mapping.setRequired(Boolean.TRUE.equals(request.required()));
            mapping.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
            mapping = categoryAttributeRepository.save(mapping);
            return ResponseEntity.ok(toCategoryAttributeResponse(mapping));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{categoryId}/attributes/{categoryAttributeId}")
    @Transactional
    public ResponseEntity<?> deleteCategoryAttribute(
            @PathVariable Long categoryId,
            @PathVariable Long categoryAttributeId
    ) {
        try {
            CategoryAttribute mapping = categoryAttributeRepository.findByCategoryIdAndId(categoryId, categoryAttributeId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thuộc tính danh mục: " + categoryAttributeId));

            Long attributeId = mapping.getAttribute().getId();
            productAttributeValueRepository.deleteByProductCategoryAndAttribute(categoryId, attributeId);
            categoryAttributeRepository.delete(mapping);

            return ResponseEntity.ok(Map.of("deleted", categoryAttributeId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createCategory(@RequestBody CategoryUpsertRequest request) {
        try {
            if (request == null || !StringUtils.hasText(request.name())) {
                return ResponseEntity.badRequest().body(Map.of("error", "name là bắt buộc"));
            }

            String name = request.name().trim();
            Long parentId = request.parentId();

            if (categoryRepository.existsByNameIgnoreCaseAndParentId(name, parentId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Danh mục cùng cấp đã tồn tại"));
            }

            Category category = new Category();
            category.setName(name);
            category.setParent(resolveParent(parentId, null));
            category = categoryRepository.save(category);
            return ResponseEntity.ok(toCategoryResponse(category));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody CategoryUpsertRequest request) {
        try {
            if (request == null || !StringUtils.hasText(request.name())) {
                return ResponseEntity.badRequest().body(Map.of("error", "name là bắt buộc"));
            }

            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục: " + id));

            String name = request.name().trim();
            Long parentId = request.parentId();

            if (categoryRepository.existsByNameIgnoreCaseAndParentIdAndIdNot(name, parentId, id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Danh mục cùng cấp đã tồn tại"));
            }

            category.setName(name);
            category.setParent(resolveParent(parentId, id));
            category = categoryRepository.save(category);
            return ResponseEntity.ok(toCategoryResponse(category));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục: " + id));

            if (categoryRepository.existsByParentId(id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Danh mục còn danh mục con, không thể xóa"));
            }
            if (productRepository.existsByCategoryId(id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Danh mục đang có sản phẩm, không thể xóa"));
            }
            if (categoryAttributeRepository.existsByCategoryId(id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Danh mục đang có thuộc tính, không thể xóa"));
            }

            categoryRepository.delete(category);
            return ResponseEntity.ok(Map.of("deleted", id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục: " + categoryId));
    }

    private AttributeDefinition resolveAttributeDefinitionForCreate(Category category, CategoryAttributeUpsertRequest request) {
        if (request.attributeId() != null) {
            AttributeDefinition existing = attributeDefinitionRepository.findById(request.attributeId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy định nghĩa thuộc tính: " + request.attributeId()));
            if (hasAttributeDefinitionPayload(request)) {
                AttributeDefinition target = cloneAttributeDefinition(existing);
                return upsertAttributeDefinition(target, request, category);
            }
            return existing;
        }
        return upsertAttributeDefinition(null, request, category);
    }

    private AttributeDefinition upsertAttributeDefinition(
            AttributeDefinition existing,
            CategoryAttributeUpsertRequest request,
            Category category
    ) {
        String defaultName = existing != null ? existing.getName() : null;
        String name = StringUtils.hasText(request.name()) ? request.name().trim() : defaultName;
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("name là bắt buộc");
        }

        AttributeType defaultType = existing != null ? existing.getType() : null;
        AttributeType type = StringUtils.hasText(request.type()) ? parseType(request.type()) : defaultType;
        if (type == null) {
            throw new IllegalArgumentException("type là bắt buộc (STRING|NUMBER|BOOLEAN)");
        }

        String providedCode = StringUtils.hasText(request.code()) ? request.code().trim() : null;
        String candidateCode = providedCode != null
                ? sanitizeCode(providedCode)
                : (existing != null && StringUtils.hasText(existing.getCode())
                ? existing.getCode()
                : sanitizeCode(category.getName() + "_" + name));
        String code = resolveUniqueCode(candidateCode, existing != null ? existing.getId() : null);

        AttributeDefinition target = existing != null ? existing : new AttributeDefinition();
        target.setCode(code);
        target.setName(name);
        target.setType(type);
        if (request.unit() != null) {
            target.setUnit(trimToNull(request.unit()));
        } else if (existing == null) {
            target.setUnit(null);
        }

        return attributeDefinitionRepository.save(target);
    }

    private boolean hasAttributeDefinitionPayload(CategoryAttributeUpsertRequest request) {
        return StringUtils.hasText(request.name())
                || StringUtils.hasText(request.type())
                || request.unit() != null
                || StringUtils.hasText(request.code());
    }

    private AttributeDefinition cloneAttributeDefinition(AttributeDefinition source) {
        AttributeDefinition cloned = new AttributeDefinition();
        cloned.setCode(source.getCode());
        cloned.setName(source.getName());
        cloned.setType(source.getType());
        cloned.setUnit(source.getUnit());
        return cloned;
    }

    private String resolveUniqueCode(String candidate, Long ignoreAttributeId) {
        String base = sanitizeCode(candidate);
        String checking = base;
        int suffix = 2;
        while (true) {
            AttributeDefinition existing = attributeDefinitionRepository.findByCodeIgnoreCase(checking).orElse(null);
            if (existing == null || (ignoreAttributeId != null && ignoreAttributeId.equals(existing.getId()))) {
                return checking;
            }
            checking = base + "_" + suffix++;
        }
    }

    private String sanitizeCode(String rawCode) {
        String base = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (base.isEmpty()) {
            base = "ATTR";
        }
        base = base.replaceAll("[^A-Z0-9_]", "_");
        base = base.replaceAll("_+", "_");
        base = base.replaceAll("^_+", "");
        base = base.replaceAll("_+$", "");
        return base.isEmpty() ? "ATTR" : base;
    }

    private AttributeType parseType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return null;
        }
        try {
            return AttributeType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("type không hợp lệ. Chỉ chấp nhận STRING|NUMBER|BOOLEAN");
        }
    }

    private CategoryAttributeResponse toCategoryAttributeResponse(CategoryAttribute item) {
        AttributeDefinition attribute = item.getAttribute();
        return new CategoryAttributeResponse(
                item.getId(),
                attribute.getId(),
                attribute.getCode(),
                attribute.getName(),
                attribute.getType() != null ? attribute.getType().name() : null,
                attribute.getUnit(),
                Boolean.TRUE.equals(item.getRequired()),
                item.getSortOrder() == null ? 0 : item.getSortOrder()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Category resolveParent(Long parentId, Long currentCategoryId) {
        if (parentId == null) {
            return null;
        }

        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục cha: " + parentId));

        if (currentCategoryId != null) {
            if (parentId.equals(currentCategoryId)) {
                throw new IllegalArgumentException("Danh mục không thể là cha của chính nó");
            }
            ensureNoParentCycle(parent, currentCategoryId);
        }

        return parent;
    }

    private void ensureNoParentCycle(Category parent, Long currentCategoryId) {
        Set<Long> visited = new HashSet<>();
        Category cursor = parent;
        while (cursor != null) {
            Long id = cursor.getId();
            if (id == null) {
                break;
            }
            if (id.equals(currentCategoryId)) {
                throw new IllegalArgumentException("Quan hệ cha-con tạo vòng lặp, không hợp lệ");
            }
            if (!visited.add(id)) {
                break;
            }
            cursor = cursor.getParent();
        }
    }

    private List<CategoryResponse> buildCategoryResponses() {
        List<Category> categories = categoryRepository.findAll();
        categories.sort(Comparator
                .comparing(Category::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(Category::getId, Comparator.nullsLast(Long::compareTo)));

        Map<Long, Category> byId = new HashMap<>();
        for (Category category : categories) {
            byId.put(category.getId(), category);
        }

        Map<Long, Long> childCount = new HashMap<>();
        for (Category category : categories) {
            if (category.getParent() == null || category.getParent().getId() == null) {
                continue;
            }
            childCount.merge(category.getParent().getId(), 1L, Long::sum);
        }

        Map<Long, Long> productCount = new HashMap<>();
        for (Product product : productRepository.findAll()) {
            if (product.getCategory() == null || product.getCategory().getId() == null) {
                continue;
            }
            productCount.merge(product.getCategory().getId(), 1L, Long::sum);
        }

        Map<Long, Long> attributeCount = new HashMap<>();
        for (CategoryAttribute categoryAttribute : categoryAttributeRepository.findAll()) {
            if (categoryAttribute.getCategory() == null || categoryAttribute.getCategory().getId() == null) {
                continue;
            }
            attributeCount.merge(categoryAttribute.getCategory().getId(), 1L, Long::sum);
        }

        List<CategoryResponse> response = new ArrayList<>();
        for (Category category : categories) {
            response.add(toCategoryResponse(category, byId, childCount, productCount, attributeCount));
        }
        return response;
    }

    private CategoryResponse toCategoryResponse(Category category) {
        Long categoryId = category.getId();
        if (categoryId == null) {
            return new CategoryResponse(
                    null,
                    category.getName(),
                    null,
                    null,
                    0L,
                    0L,
                    0L,
                    0,
                    category.getName()
            );
        }
        for (CategoryResponse item : buildCategoryResponses()) {
            if (categoryId.equals(item.id())) {
                return item;
            }
        }
        return new CategoryResponse(
                categoryId,
                category.getName(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getParent() != null ? category.getParent().getName() : null,
                0L,
                0L,
                0L,
                0,
                category.getName()
        );
    }

    private CategoryResponse toCategoryResponse(
            Category category,
            Map<Long, Category> byId,
            Map<Long, Long> childCount,
            Map<Long, Long> productCount,
            Map<Long, Long> attributeCount
    ) {
        Long id = category.getId();
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        String parentName = category.getParent() != null ? category.getParent().getName() : null;

        List<String> pathParts = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Category cursor = category;
        while (cursor != null && cursor.getId() != null && visited.add(cursor.getId())) {
            pathParts.add(cursor.getName() == null ? "" : cursor.getName());
            Long cursorParentId = cursor.getParent() != null ? cursor.getParent().getId() : null;
            cursor = cursorParentId != null ? byId.get(cursorParentId) : null;
        }
        Collections.reverse(pathParts);
        int depth = Math.max(0, pathParts.size() - 1);
        String path = String.join(" / ", pathParts);

        return new CategoryResponse(
                id,
                category.getName(),
                parentId,
                parentName,
                productCount.getOrDefault(id, 0L),
                childCount.getOrDefault(id, 0L),
                attributeCount.getOrDefault(id, 0L),
                depth,
                path
        );
    }

    public record CategoryUpsertRequest(String name, Long parentId) {
    }

    public record CategoryAttributeUpsertRequest(
            Long attributeId,
            String code,
            String name,
            String type,
            String unit,
            Boolean required,
            Integer sortOrder
    ) {
    }

    public record CategoryResponse(
            Long id,
            String name,
            Long parentId,
            String parentName,
            Long productCount,
            Long childCount,
            Long attributeCount,
            Integer depth,
            String path
    ) {
    }

    public record CategoryAttributeResponse(
            Long id,
            Long attributeId,
            String code,
            String name,
            String type,
            String unit,
            Boolean required,
            Integer sortOrder
    ) {
    }
}
