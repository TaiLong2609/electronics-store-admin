package com.example.ecomerce.security;

public enum Permission {
    PRODUCT_VIEW("PRODUCT_VIEW"),
    PRODUCT_CREATE("PRODUCT_CREATE"),
    PRODUCT_UPDATE("PRODUCT_UPDATE"),
    PRODUCT_DELETE("PRODUCT_DELETE"),
    ORDER_VIEW_SELF("ORDER_VIEW_SELF"),
    ORDER_VIEW_ALL("ORDER_VIEW_ALL"),
    ORDER_CREATE("ORDER_CREATE"),
    ORDER_MODIFY_STATUS("ORDER_MODIFY_STATUS"),
    CART_VIEW("CART_VIEW"),
    CART_MODIFY("CART_MODIFY"),
    USER_MANAGE("USER_MANAGE"),
    STATS_VIEW("STATS_VIEW"),
    MARKETING_MANAGE("MARKETING_MANAGE");

    private final String permission;

    Permission(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}
