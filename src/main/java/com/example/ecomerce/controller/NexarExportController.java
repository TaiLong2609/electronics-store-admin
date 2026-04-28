package com.example.ecomerce.controller;

import com.example.ecomerce.service.NexarSqlExportService;
import com.example.ecomerce.service.InventoryService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class NexarExportController {

    private final NexarSqlExportService nexarSqlExportService;
    private final InventoryService inventoryService;

    public NexarExportController(NexarSqlExportService nexarSqlExportService, InventoryService inventoryService) {
        this.nexarSqlExportService = nexarSqlExportService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/category-options")
    public Object listCategoryOptions() {
        return inventoryService.listCategories();
    }

    @GetMapping("/export-components")
    public ResponseEntity<byte[]> exportComponentsSql(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "categoryId", required = false) Long categoryId
    ) {
        String sqlContent = exportSql(keyword, limit, categoryId, null);
        byte[] fileBytes = sqlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "nexar-components-" + LocalDate.now() + ".sql";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(fileName).build().toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(fileBytes);
        }

        @PostMapping("/export-components")
        public ResponseEntity<byte[]> exportComponentsSqlByBody(
            @RequestBody(required = false) ExportRequest request
        ) {
        String keyword = request != null ? request.q() : null;
        Integer limit = request != null ? request.limit() : null;
        Long categoryId = request != null ? request.categoryId() : null;
        NexarSqlExportService.NexarRuntimeOptions runtimeOptions = null;

        if (request != null && request.nexar() != null) {
            runtimeOptions = new NexarSqlExportService.NexarRuntimeOptions(
                request.nexar().clientId(),
                request.nexar().clientSecret(),
                request.nexar().scope(),
                request.nexar().tokenUrl(),
                request.nexar().graphqlUrl()
            );
        }

        String sqlContent = exportSql(keyword, limit, categoryId, runtimeOptions);
        byte[] fileBytes = sqlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "nexar-components-" + LocalDate.now() + ".sql";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileBytes);
    }

    private String exportSql(
            String keyword,
            Integer limit,
            Long categoryId,
            NexarSqlExportService.NexarRuntimeOptions runtimeOptions
    ) {
        try {
            return nexarSqlExportService.exportFromNexarAsSql(keyword, limit, categoryId, runtimeOptions);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    public record ExportRequest(
            String q,
            Integer limit,
            Long categoryId,
            NexarAuthRequest nexar
    ) {
    }

    public record NexarAuthRequest(
            String clientId,
            String clientSecret,
            String scope,
            String tokenUrl,
            String graphqlUrl
    ) {
    }
}
