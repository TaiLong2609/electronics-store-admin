package com.example.ecomerce.service;

import com.example.ecomerce.models.CartItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import org.springframework.util.StringUtils;

@Service
public class CartService {

    private final Map<String, List<CartItem>> carts = new ConcurrentHashMap<>();
    private final Map<String, String> appliedVoucherCodes = new ConcurrentHashMap<>();

    public List<CartItem> getCart(String username) {
        return Collections.unmodifiableList(carts.getOrDefault(username, new ArrayList<>()));
    }

    public List<CartItem> addItem(String username, CartItem item) {
        var list = carts.computeIfAbsent(username, key -> new ArrayList<>());
        var existing = list.stream()
                .filter(cartItem -> cartItem.getProductId().equals(item.getProductId()))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + item.getQuantity());
        } else {
            list.add(item);
        }
        return Collections.unmodifiableList(list);
    }

    public List<CartItem> removeItem(String username, Long productId) {
        var list = carts.getOrDefault(username, new ArrayList<>());
        list.removeIf(item -> item.getProductId().equals(productId));
        if (list.isEmpty()) {
            appliedVoucherCodes.remove(username);
        }
        return Collections.unmodifiableList(list);
    }

    public Optional<String> getVoucherCode(String username) {
        return Optional.ofNullable(appliedVoucherCodes.get(username));
    }

    public void applyVoucherCode(String username, String rawCode) {
        String code = normalizeCode(rawCode);
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code voucher la bat buoc");
        }
        appliedVoucherCodes.put(username, code);
    }

    public void clearVoucherCode(String username) {
        appliedVoucherCodes.remove(username);
    }

    private String normalizeCode(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            return null;
        }
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }
}
