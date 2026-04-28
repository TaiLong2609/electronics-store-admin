package com.example.ecomerce.controller;

import com.example.ecomerce.service.MouserSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
public class TestApiController {

    private final MouserSyncService mouserSyncService;

    public TestApiController(MouserSyncService mouserSyncService) {
        this.mouserSyncService = mouserSyncService;
    }

    // Example: http://localhost:8080/test-mouser?keyword=ESP32
    @GetMapping("/test-mouser")
    public String testMouserApi(@RequestParam(defaultValue = "resistor") String keyword) {
        return mouserSyncService.testFetchData(keyword);
    }

    @PostMapping("/api/admin/export-components-mouser")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> syncMouserComponents(@RequestBody(required = false) MouserExportRequest request) {
        try {
            String keyword = resolveKeyword(request);
            Integer limit = request != null ? request.limit() : null;
            Long categoryId = request != null ? request.categoryId() : null;
            String apiKey = request != null ? request.apiKey() : null;

            MouserSyncService.SyncResult result = mouserSyncService.syncFromMouser(keyword, limit, categoryId, apiKey);
            return ResponseEntity.ok(Map.of(
                    "message", "Đồng bộ linh kiện Mouser vào database thành công.",
                    "keyword", keyword == null || keyword.isBlank() ? "ESP32" : keyword.trim(),
                    "fetched", result.fetched(),
                    "inserted", result.inserted(),
                    "updated", result.updated(),
                    "skipped", result.skipped()
            ));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    public record MouserExportRequest(
            String q,
            String keyword,
            Integer limit,
            Long categoryId,
            String apiKey
    ) {
    }

    private String resolveKeyword(MouserExportRequest request) {
        if (request == null) {
            return null;
        }
        if (request.keyword() != null && !request.keyword().isBlank()) {
            return request.keyword();
        }
        if (request.q() != null && !request.q().isBlank()) {
            return request.q();
        }
        return null;
    }
}
