package com.example.ecomerce.security;

import java.util.List;

public enum Role {
    SUPER_ADMIN(List.of(Permission.values())),
    SALES(List.of(
            Permission.ORDER_CREATE,
            Permission.ORDER_VIEW_ALL,
            Permission.ORDER_MODIFY_STATUS,
            Permission.PRODUCT_VIEW
    )),
    WAREHOUSE(List.of(
            Permission.PRODUCT_VIEW,
            Permission.PRODUCT_CREATE,
            Permission.PRODUCT_UPDATE,
            Permission.PRODUCT_DELETE
    )),
    CUSTOMER(List.of(
            Permission.PRODUCT_VIEW,
            Permission.CART_VIEW,
            Permission.CART_MODIFY,
        Permission.ORDER_VIEW_SELF,
            Permission.ORDER_CREATE
    )),
    CONTENT(List.of(Permission.MARKETING_MANAGE));

    private final List<Permission> permissions;

    Role(List<Permission> permissions) {
        this.permissions = permissions;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }
}
