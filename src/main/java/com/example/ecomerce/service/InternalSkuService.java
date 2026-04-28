package com.example.ecomerce.service;

import com.example.ecomerce.models.Product;
import com.example.ecomerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InternalSkuService {

    private static final Pattern INTERNAL_SKU_PATTERN = Pattern.compile("(?i)^SKU-(\\d+)$");
    private static final long START_NUMBER = 1001L;

    private final ProductRepository productRepository;

    public InternalSkuService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public synchronized Product ensureSku(Product product) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("Không thể sinh SKU do thiếu product id");
        }
        if (StringUtils.hasText(product.getSku())) {
            return product;
        }

        long candidateNumber = Math.max(nextSkuNumberFromData(), 1000L + product.getId());
        String candidateSku = "SKU-" + candidateNumber;
        while (skuExistsForOtherProduct(candidateSku, product.getId())) {
            candidateNumber++;
            candidateSku = "SKU-" + candidateNumber;
        }

        product.setSku(candidateSku);
        return productRepository.save(product);
    }

    private long nextSkuNumberFromData() {
        long max = START_NUMBER - 1;
        for (Product product : productRepository.findAll()) {
            Long number = extractSkuNumber(product.getSku());
            if (number != null && number > max) {
                max = number;
            }
        }
        return max + 1;
    }

    private Long extractSkuNumber(String sku) {
        if (!StringUtils.hasText(sku)) {
            return null;
        }
        Matcher matcher = INTERNAL_SKU_PATTERN.matcher(sku.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean skuExistsForOtherProduct(String sku, Long currentProductId) {
        return productRepository.findFirstBySkuIgnoreCase(sku)
                .filter(existing -> !existing.getId().equals(currentProductId))
                .isPresent();
    }
}
