package com.example.ecomerce.service;

import com.example.ecomerce.config.NexarProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class NexarSqlExportService {

        private static final String ENRICHED_SUP_SEARCH_QUERY = """
                        query SearchComponents($q: String!, $limit: Int!) {
                            supSearchMpn(q: $q, limit: $limit) {
                                results {
                                    part {
                                        mpn
                                        manufacturer {
                                            name
                                        }
                                        shortDescription
                                        totalAvail
                                        sellers {
                                            offers {
                                                inventoryLevel
                                                prices {
                                                    quantity
                                                    price
                                                    currency
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        """;

        private static final String BASIC_SUP_SEARCH_QUERY = """
            query SearchComponents($q: String!, $limit: Int!) {
              supSearchMpn(q: $q, limit: $limit) {
                results {
                  part {
                    mpn
                    manufacturer {
                      name
                    }
                    shortDescription
                  }
                }
              }
            }
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NexarProperties nexarProperties;
    private final NexarOAuthService nexarOAuthService;

    public NexarSqlExportService(
            ObjectMapper objectMapper,
            NexarProperties nexarProperties,
            NexarOAuthService nexarOAuthService
    ) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
        this.nexarProperties = nexarProperties;
        this.nexarOAuthService = nexarOAuthService;
    }

    public String exportFromNexarAsSql(String queryKeyword, Integer limit, Long categoryId) {
        return exportFromNexarAsSql(queryKeyword, limit, categoryId, null);
    }

    public String exportFromNexarAsSql(
            String queryKeyword,
            Integer limit,
            Long categoryId,
            NexarRuntimeOptions runtimeOptions
    ) {
        String keyword = StringUtils.hasText(queryKeyword) ? queryKeyword.trim() : "ACS770LCB-050U-PFF-T";
        int safeLimit = limit != null && limit > 0 ? limit : nexarProperties.getDefaultLimit();
        String token = nexarOAuthService.getAccessToken(
                runtimeOptions != null ? runtimeOptions.clientId() : null,
                runtimeOptions != null ? runtimeOptions.clientSecret() : null,
                runtimeOptions != null ? runtimeOptions.scope() : null,
                runtimeOptions != null ? runtimeOptions.tokenUrl() : null
        );
        String graphqlUrl = resolveGraphqlUrl(runtimeOptions);
        JsonNode root = fetchNexarResponse(token, graphqlUrl, keyword, safeLimit);
        return generateSqlFromResponse(root, categoryId);
    }

    public String generateSqlFromJson(String nexarJsonResponse, Long categoryId) {
        try {
            JsonNode root = objectMapper.readTree(nexarJsonResponse);
            return generateSqlFromResponse(root, categoryId);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate SQL from Nexar JSON.", ex);
        }
    }

    private String generateSqlFromResponse(JsonNode root, Long categoryId) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("-- Generated from Nexar GraphQL at ").append(OffsetDateTime.now()).append("\n");
        sqlBuilder.append("START TRANSACTION;\n\n");

        int insertCount = 0;

        JsonNode results = root.path("data").path("supSearchMpn").path("results");

        if (results.isArray()) {
            for (JsonNode node : results) {
                JsonNode partNode = node.path("part");
                PartSnapshot part = extractPartSnapshot(partNode);

                if (!StringUtils.hasText(part.sku())) {
                    continue;
                }

                sqlBuilder.append(buildInsertLine(part, categoryId));
                insertCount++;
            }
        }

        sqlBuilder.append("\nCOMMIT;\n");
        sqlBuilder.append("-- Total inserts: ").append(insertCount).append("\n");
        return sqlBuilder.toString();
    }

    private JsonNode fetchNexarResponse(String token, String graphqlUrl, String keyword, int limit) {
        GraphqlRequest enrichedRequest = new GraphqlRequest(
                ENRICHED_SUP_SEARCH_QUERY,
                Map.of("q", keyword, "limit", limit)
        );
        StringBuilder failures = new StringBuilder();
        JsonNode enrichedResponse = tryExecuteGraphql(token, graphqlUrl, enrichedRequest, "enriched", failures);
        if (enrichedResponse != null && !hasErrors(enrichedResponse)) {
            return enrichedResponse;
        }
        if (enrichedResponse != null && hasErrors(enrichedResponse)) {
            appendFailure(failures, "enriched-errors", extractGraphqlErrorSummary(enrichedResponse));
        }

        GraphqlRequest fallbackRequest = new GraphqlRequest(
                BASIC_SUP_SEARCH_QUERY,
                Map.of("q", keyword, "limit", limit)
        );
        JsonNode fallbackResponse = tryExecuteGraphql(token, graphqlUrl, fallbackRequest, "fallback", failures);
        if (fallbackResponse == null) {
            throw new IllegalStateException("Nexar GraphQL request failed for both enriched and fallback queries. " + failures);
        }
        if (hasErrors(fallbackResponse)) {
            throw new IllegalStateException("Nexar GraphQL returned errors: " + extractGraphqlErrorSummary(fallbackResponse));
        }
        return fallbackResponse;
    }

    private JsonNode tryExecuteGraphql(
            String token,
            String graphqlUrl,
            GraphqlRequest request,
            String queryLabel,
            StringBuilder failures
    ) {
        try {
            return executeGraphql(token, graphqlUrl, request);
        } catch (IllegalStateException ex) {
            appendFailure(failures, queryLabel, ex.getMessage());
            return null;
        }
    }

    private void appendFailure(StringBuilder failures, String label, String message) {
        if (failures.length() > 0) {
            failures.append(" | ");
        }
        failures.append(label).append(": ").append(compactMessage(message));
    }

    private String compactMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 320) {
            return normalized.substring(0, 320) + "...";
        }
        return normalized;
    }

    private String extractGraphqlErrorSummary(JsonNode response) {
        JsonNode errors = response.path("errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return "unknown GraphQL error";
        }

        StringBuilder summary = new StringBuilder();
        int index = 0;
        for (JsonNode error : errors) {
            if (index > 0) {
                summary.append(" | ");
            }

            String message = error.path("message").asText("").trim();
            if (!StringUtils.hasText(message)) {
                message = "GraphQL error without message";
            }

            JsonNode path = error.path("path");
            if (path.isArray() && !path.isEmpty()) {
                StringBuilder pathBuilder = new StringBuilder();
                int segment = 0;
                for (JsonNode item : path) {
                    if (segment > 0) {
                        pathBuilder.append(".");
                    }
                    pathBuilder.append(item.asText("?"));
                    segment++;
                }
                message = message + " (path=" + pathBuilder + ")";
            }

            summary.append(compactMessage(message));
            index++;

            if (index >= 3) {
                break;
            }
        }

        return summary.toString();
    }

    private JsonNode executeGraphql(String token, String graphqlUrl, GraphqlRequest request) {
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(graphqlUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("Nexar GraphQL request failed: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Nexar GraphQL transport failed: " + compactMessage(ex.getMessage()), ex);
        }

        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("Nexar GraphQL returned empty response.");
        }

        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Nexar GraphQL response parse failed: " + compactMessage(ex.getMessage()) +
                            " | body=" + compactMessage(responseBody),
                    ex
            );
        }
    }

    private String resolveGraphqlUrl(NexarRuntimeOptions runtimeOptions) {
        if (runtimeOptions != null && StringUtils.hasText(runtimeOptions.graphqlUrl())) {
            return runtimeOptions.graphqlUrl().trim();
        }
        return nexarProperties.getGraphqlUrl();
    }

    private boolean hasErrors(JsonNode response) {
        JsonNode errors = response.path("errors");
        return errors.isArray() && !errors.isEmpty();
    }

    private PartSnapshot extractPartSnapshot(JsonNode part) {
        String sku = part.path("mpn").asText("").trim();
        String manufacturer = part.path("manufacturer").path("name").asText("").trim();
        String shortDescription = part.path("shortDescription").asText("").trim();

        String productName = StringUtils.hasText(manufacturer)
                ? manufacturer + " " + sku
                : sku;
        String description = StringUtils.hasText(shortDescription)
                ? shortDescription
                : "Imported from Nexar";

        return new PartSnapshot(
                productName,
                sku,
                description,
                resolvePrice(part),
                resolveInventoryQuantity(part)
        );
    }

    private BigDecimal resolvePrice(JsonNode part) {
        JsonNode sellers = part.path("sellers");
        if (!sellers.isArray()) {
            return null;
        }

        BigDecimal bestPrice = null;
        for (JsonNode seller : sellers) {
            JsonNode offers = seller.path("offers");
            if (!offers.isArray()) {
                continue;
            }

            for (JsonNode offer : offers) {
                JsonNode prices = offer.path("prices");
                if (!prices.isArray()) {
                    continue;
                }

                for (JsonNode tier : prices) {
                    BigDecimal candidate = numberFromNode(tier.path("price"));
                    if (candidate == null) {
                        continue;
                    }
                    if (bestPrice == null || candidate.compareTo(bestPrice) < 0) {
                        bestPrice = candidate;
                    }
                }
            }
        }
        return bestPrice;
    }

    private Integer resolveInventoryQuantity(JsonNode part) {
        JsonNode totalAvail = part.path("totalAvail");
        if (totalAvail.isNumber()) {
            return Math.max(0, totalAvail.asInt());
        }

        JsonNode totalAvailability = part.path("totalAvailability");
        if (totalAvailability.isNumber()) {
            return Math.max(0, totalAvailability.asInt());
        }

        JsonNode sellers = part.path("sellers");
        if (!sellers.isArray()) {
            return null;
        }

        int total = 0;
        boolean found = false;
        for (JsonNode seller : sellers) {
            JsonNode offers = seller.path("offers");
            if (!offers.isArray()) {
                continue;
            }

            for (JsonNode offer : offers) {
                JsonNode inventoryLevel = offer.path("inventoryLevel");
                if (!inventoryLevel.isNumber()) {
                    continue;
                }
                total += Math.max(0, inventoryLevel.asInt());
                found = true;
            }
        }
        return found ? total : null;
    }

    private BigDecimal numberFromNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String raw = node.asText().trim();
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String buildInsertLine(PartSnapshot part, Long categoryId) {
        String safeName = sqlEscape(part.name());
        String safeSku = sqlEscape(part.sku());
        String safeDescription = sqlEscape(part.description());

        String priceSql = part.price() == null
                ? "NULL"
                : part.price().stripTrailingZeros().toPlainString();
        String inventorySql = part.inventoryQuantity() == null
                ? "NULL"
                : String.valueOf(part.inventoryQuantity());

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

    private String sqlEscape(String value) {
        String normalized = value == null ? "" : value.replace("\n", " ").replace("\r", " ").trim();
        return normalized.replace("'", "''");
    }

    private record GraphqlRequest(String query, Map<String, Object> variables) {
    }

        public record NexarRuntimeOptions(
            String clientId,
            String clientSecret,
            String scope,
            String tokenUrl,
            String graphqlUrl
        ) {
        }

    private record PartSnapshot(
            String name,
            String sku,
            String description,
            BigDecimal price,
            Integer inventoryQuantity
    ) {
    }
}
