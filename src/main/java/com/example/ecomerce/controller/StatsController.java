package com.example.ecomerce.controller;

import com.example.ecomerce.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final ProductRepository productRepository;

    @Autowired
    public StatsController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('STATS_VIEW')")
    public Map<String, Object> productStats() {
        var products = productRepository.findAll();
        var totalProducts = products.size();
        var totalValue = products.stream()
                .mapToDouble(p -> p.getPrice() == null ? 0.0 : p.getPrice())
                .sum();

        return Map.of(
                "totalProducts", totalProducts,
                "totalValue", totalValue,
                "averagePrice", totalProducts == 0 ? 0.0 : totalValue / totalProducts
        );
    }
}
