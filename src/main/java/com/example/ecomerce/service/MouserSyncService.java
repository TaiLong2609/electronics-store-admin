package com.example.ecomerce.service;

import com.example.ecomerce.models.Category;
import com.example.ecomerce.models.Product;
import com.example.ecomerce.repository.CategoryRepository;
import com.example.ecomerce.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MouserSyncService {

    private static final int DEFAULT_LIMIT = 20;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InternalSkuService internalSkuService;

    @Value("${mouser.api.key}")
    private String configuredApiKey;

    public MouserSyncService(
            ObjectMapper objectMapper,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            InternalSkuService internalSkuService
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.internalSkuService = internalSkuService;
    }

    public String testFetchData(String keyword) {
        try {
            JsonNode root = fetchMouserResponse(keyword, 10, null);
            return root.toPrettyString();
        } catch (Exception ex) {
            return "Error calling Mouser API: " + ex.getMessage();
        }
    }

    public String exportFromMouserAsSql(String keyword, Integer limit, Long categoryId) {
        return exportFromMouserAsSql(keyword, limit, categoryId, null);
    }

    public String exportFromMouserAsSql(String keyword, Integer limit, Long categoryId, String apiKeyOverride) {
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "ESP32";
        int safeLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;

        JsonNode root = fetchMouserResponse(safeKeyword, safeLimit, apiKeyOverride);
        assertNoMouserErrors(root);

        StringBuilder sql = new StringBuilder();
        sql.append("-- Generated from Mouser API at ").append(OffsetDateTime.now()).append("\n");
        sql.append("START TRANSACTION;\n\n");

        int insertCount = 0;
        JsonNode parts = root.path("SearchResults").path("Parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                String sku = safeText(part.path("ManufacturerPartNumber"));
                if (!StringUtils.hasText(sku)) {
                    sku = safeText(part.path("MouserPartNumber"));
                }
                if (!StringUtils.hasText(sku)) {
                    continue;
                }

                String manufacturer = safeText(part.path("Manufacturer"));
                String name = StringUtils.hasText(manufacturer) ? manufacturer + " " + sku : sku;

                String description = safeText(part.path("Description"));
                if (!StringUtils.hasText(description)) {
                    description = "Imported from Mouser";
                }

                BigDecimal price = resolvePrice(part.path("PriceBreaks"));
                Integer inventory = resolveAvailability(part.path("Availability"));

                sql.append(buildInsertLine(name, sku, description, price, inventory, categoryId));
                insertCount++;
            }
        }

        sql.append("\nCOMMIT;\n");
        sql.append("-- Total inserts: ").append(insertCount).append("\n");
        return sql.toString();
    }

    @Transactional
    public SyncResult syncFromMouser(String keyword, Integer limit, Long categoryId, String apiKeyOverride) {
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "ESP32";
        int safeLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;

        Category category = null;
        if (categoryId != null) {
            category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy categoryId: " + categoryId));
        }

        JsonNode root = fetchMouserResponse(safeKeyword, safeLimit, apiKeyOverride);
        assertNoMouserErrors(root);

        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        JsonNode parts = root.path("SearchResults").path("Parts");
        if (!parts.isArray()) {
            return new SyncResult(0, inserted, updated, skipped);
        }

        for (JsonNode part : parts) {
            String mpn = safeText(part.path("ManufacturerPartNumber"));
            if (!StringUtils.hasText(mpn)) {
                skipped++;
                continue;
            }

            String manufacturer = safeText(part.path("Manufacturer"));
            String name = StringUtils.hasText(manufacturer) ? manufacturer + " " + mpn : mpn;

            String description = safeText(part.path("Description"));
            if (!StringUtils.hasText(description)) {
                description = "Imported from Mouser";
            }

            BigDecimal price = resolvePrice(part.path("PriceBreaks"));
            Integer inventory = resolveAvailability(part.path("Availability"));

            Product product = productRepository.findFirstByMpnIgnoreCase(mpn)
                    .or(() -> productRepository.findFirstBySkuIgnoreCase(mpn))
                    .orElseGet(Product::new);
            boolean isInsert = product.getId() == null;

            product.setMpn(mpn);
            product.setName(name);
            product.setDescription(description);
            if (price != null || isInsert) {
                product.setPrice(price == null ? null : price.doubleValue());
            }
            if (inventory != null) {
                product.setInventoryQuantity(Math.max(0, inventory));
            } else if (isInsert && product.getInventoryQuantity() == null) {
                product.setInventoryQuantity(0);
            }
            if (category != null) {
                product.setCategory(category);
            }

            product = productRepository.save(product);
            product = internalSkuService.ensureSku(product);
            if (isInsert) {
                inserted++;
            } else {
                updated++;
            }
        }

        return new SyncResult(parts.size(), inserted, updated, skipped);
    }

    private JsonNode fetchMouserResponse(String keyword, int records, String apiKeyOverride) {
        String apiKey = StringUtils.hasText(apiKeyOverride) ? apiKeyOverride.trim() : configuredApiKey;
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing Mouser API key. Set mouser.api.key in application.properties.");
        }

        String url = "https://api.mouser.com/api/v1/search/keyword?apiKey=" + apiKey;

        Map<String, Object> searchOptions = new HashMap<>();
        searchOptions.put("keyword", keyword);
        searchOptions.put("records", records);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("SearchByKeywordRequest", searchOptions);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                throw new IllegalStateException("Mouser API returned empty body.");
            }
            return objectMapper.readTree(body);
        } catch (HttpStatusCodeException ex) {
            throw new IllegalStateException("Mouser request failed: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Mouser request failed: " + ex.getMessage(), ex);
        }
    }

    private void assertNoMouserErrors(JsonNode root) {
        JsonNode errors = root.path("Errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (JsonNode error : errors) {
            String message = safeText(error.path("Message"));
            if (!StringUtils.hasText(message)) {
                message = error.asText("").trim();
            }
            if (!StringUtils.hasText(message)) {
                message = "Unknown Mouser API error";
            }

            if (count > 0) {
                sb.append(" | ");
            }
            sb.append(message);
            count++;
            if (count >= 3) {
                break;
            }
        }

        throw new IllegalStateException("Mouser API returned errors: " + sb);
    }

    private BigDecimal resolvePrice(JsonNode priceBreaks) {
        if (!priceBreaks.isArray()) {
            return null;
        }

        BigDecimal min = null;
        for (JsonNode priceBreak : priceBreaks) {
            String rawPrice = safeText(priceBreak.path("Price"));
            if (!StringUtils.hasText(rawPrice)) {
                continue;
            }

            String normalized = rawPrice.replaceAll("[^0-9.,-]", "");
            normalized = normalized.replace(",", "");
            if (!StringUtils.hasText(normalized)) {
                continue;
            }

            try {
                BigDecimal value = new BigDecimal(normalized);
                if (min == null || value.compareTo(min) < 0) {
                    min = value;
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid price entry.
            }
        }
        return min;
    }

    private Integer resolveAvailability(JsonNode availabilityNode) {
        String availability = availabilityNode.asText("").trim();
        if (!StringUtils.hasText(availability)) {
            return null;
        }

        Matcher matcher = Pattern.compile("(\\d[\\d,]*)").matcher(availability);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Integer.parseInt(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildInsertLine(
            String name,
            String sku,
            String description,
            BigDecimal price,
            Integer inventory,
            Long categoryId
    ) {
        String safeName = sqlEscape(name);
        String safeSku = sqlEscape(sku);
        String safeDescription = sqlEscape(description);

        String priceSql = price == null ? "NULL" : price.stripTrailingZeros().toPlainString();
        String inventorySql = inventory == null ? "NULL" : String.valueOf(Math.max(0, inventory));

        if (categoryId == null) {
            return String.format(
                    "INSERT INTO products (name, sku, description, price, barcode, warranty_months, inventory_quantity) VALUES ('%s', '%s', '%s', %s, NULL, NULL, %s);%n",
                    safeName,
                    safeSku,
                    safeDescription,
                    priceSql,
                    inventorySql
            );
        }

        return String.format(
                "INSERT INTO products (name, sku, description, price, barcode, warranty_months, inventory_quantity, category_id) VALUES ('%s', '%s', '%s', %s, NULL, NULL, %s, %d);%n",
                safeName,
                safeSku,
                safeDescription,
                priceSql,
                inventorySql,
                categoryId
        );
    }

    private String safeText(JsonNode node) {
        return node == null ? "" : node.asText("").trim();
    }

    private String sqlEscape(String value) {
        String normalized = value == null ? "" : value.replace("\n", " ").replace("\r", " ").trim();
        return normalized.replace("'", "''");
    }

    public record SyncResult(int fetched, int inserted, int updated, int skipped) {
    }
}
