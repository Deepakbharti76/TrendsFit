package in.trendsfit.domain;

import java.util.EnumSet;
import java.util.Set;

/** Roles and the permissions each role gets. Every protected admin API checks a permission, not just "is admin". */
public enum Role {
    CUSTOMER, SUPER_ADMIN, ADMIN, PRODUCT_MANAGER, ORDER_MANAGER, SUPPORT_MANAGER;

    public enum Permission {
        PRODUCT_READ, PRODUCT_WRITE, INVENTORY_WRITE, COUPON_WRITE, ORDER_READ, ORDER_WRITE, SHIPMENT_WRITE,
        EXCHANGE_DECIDE, CUSTOMER_READ, REVIEW_MODERATE, ANALYTICS_READ, AUDIT_READ, USER_ROLE_WRITE, NEWSLETTER_READ, SETTINGS_WRITE,
        SUPPORT_CHAT, CONTENT_WRITE, PAYMENT_READ
    }

    public boolean isStaff() { return this != CUSTOMER; }

    public Set<Permission> permissions() {
        EnumSet<Permission> all = EnumSet.allOf(Permission.class);
        return switch (this) {
            case CUSTOMER -> EnumSet.noneOf(Permission.class);
            case SUPER_ADMIN -> all;
            case ADMIN -> { all.remove(Permission.USER_ROLE_WRITE); yield all; }
            case PRODUCT_MANAGER -> EnumSet.of(Permission.PRODUCT_READ, Permission.PRODUCT_WRITE, Permission.INVENTORY_WRITE, Permission.ANALYTICS_READ, Permission.CONTENT_WRITE);
            case ORDER_MANAGER -> EnumSet.of(Permission.PRODUCT_READ, Permission.ORDER_READ, Permission.ORDER_WRITE, Permission.SHIPMENT_WRITE,
                    Permission.EXCHANGE_DECIDE, Permission.CUSTOMER_READ, Permission.ANALYTICS_READ, Permission.PAYMENT_READ, Permission.SUPPORT_CHAT);
            case SUPPORT_MANAGER -> EnumSet.of(Permission.PRODUCT_READ, Permission.ORDER_READ, Permission.EXCHANGE_DECIDE,
                    Permission.CUSTOMER_READ, Permission.REVIEW_MODERATE, Permission.SUPPORT_CHAT);
        };
    }

    public boolean can(Permission p) { return permissions().contains(p); }
}
