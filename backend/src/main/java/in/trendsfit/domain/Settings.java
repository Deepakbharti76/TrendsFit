package in.trendsfit.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** The list of settings an admin may change, with validation. Secrets are deliberately NOT here. Pure Java, unit-tested. */
public final class Settings {
    private Settings() {}

    public enum Type { TEXT, EMAIL, BOOL, INT, MONEY, DECIMAL }

    public record Def(String key, String group, String label, Type type, double min, double max) {}

    public static final List<Def> DEFS = List.of(
        new Def("business.name", "Store", "Store name", Type.TEXT, 1, 80),
        new Def("business.email", "Store", "Support email", Type.EMAIL, 0, 0),
        new Def("business.whatsapp", "Store", "WhatsApp number", Type.TEXT, 0, 20),
        new Def("business.address", "Store", "Business address (shown on invoice)", Type.TEXT, 0, 300),
        new Def("cod.enabled", "Payments", "Cash on Delivery enabled", Type.BOOL, 0, 0),
        new Def("cod.maxOrderValue", "Payments", "COD maximum order value (₹)", Type.MONEY, 0, 1_000_000),
        new Def("payment.expiryMinutes", "Payments", "Unpaid online order expiry (minutes)", Type.INT, 5, 1440),
        new Def("shipping.freeForAll", "Shipping", "Free shipping on all orders", Type.BOOL, 0, 0),
        new Def("shipping.freeAbove", "Shipping", "Free shipping above (₹, 0 = never)", Type.MONEY, 0, 1_000_000),
        new Def("shipping.flatRate", "Shipping", "Flat shipping charge (₹)", Type.MONEY, 0, 100_000),
        new Def("shiprocket.pickupLocation", "Shipping", "Shiprocket pickup location name", Type.TEXT, 1, 80),
        new Def("shiprocket.pickupPostcode", "Shipping", "Pickup pincode", Type.TEXT, 0, 6),
        new Def("shiprocket.defaultWeightKg", "Shipping", "Default parcel weight (kg)", Type.DECIMAL, 0.05, 50),
        new Def("shiprocket.lengthCm", "Shipping", "Parcel length (cm)", Type.INT, 1, 200),
        new Def("shiprocket.breadthCm", "Shipping", "Parcel breadth (cm)", Type.INT, 1, 200),
        new Def("shiprocket.heightCm", "Shipping", "Parcel height (cm)", Type.INT, 1, 200),
        new Def("exchange.windowDays", "Exchange", "Exchange window after delivery (days)", Type.INT, 1, 30),
        new Def("inventory.lowStockThreshold", "Inventory", "Low stock alert at or below", Type.INT, 0, 1000),
        new Def("revenue.target.today", "Revenue targets", "Revenue target for Today (₹, 0 = not set)", Type.MONEY, 0, 100_000_000),
        new Def("revenue.target.7d", "Revenue targets", "Revenue target for 7 days (₹, 0 = not set)", Type.MONEY, 0, 100_000_000),
        new Def("revenue.target.30d", "Revenue targets", "Revenue target for 30 days (₹, 0 = not set)", Type.MONEY, 0, 100_000_000),
        new Def("notify.newOrder", "Notifications", "Show new orders", Type.BOOL, 0, 0),
        new Def("notify.paymentFailed", "Notifications", "Show payment failures", Type.BOOL, 0, 0),
        new Def("notify.cod", "Notifications", "Show COD orders", Type.BOOL, 0, 0),
        new Def("notify.stock", "Notifications", "Show low-stock and out-of-stock items", Type.BOOL, 0, 0),
        new Def("notify.exchange", "Notifications", "Show exchange requests", Type.BOOL, 0, 0),
        new Def("notify.refund", "Notifications", "Show refund activity", Type.BOOL, 0, 0),
        new Def("notify.message", "Notifications", "Show new customer messages", Type.BOOL, 0, 0)
    );

    /** Value used when nothing has been saved yet, for settings that are not backed by an environment variable. */
    public static String defaultFor(String key) {
        if (key.startsWith("notify.")) return "true";
        if (key.startsWith("revenue.target.")) return "0";
        return "";
    }

    public static Optional<Def> find(String key) { return DEFS.stream().filter(d -> d.key().equals(key)).findFirst(); }

    /** Returns the cleaned value to store, or throws IllegalArgumentException with a message safe to show the admin. */
    public static String clean(Def d, String raw) {
        String v = raw == null ? "" : raw.trim();
        switch (d.type()) {
            case TEXT -> {
                if (v.length() > d.max()) throw new IllegalArgumentException(d.label() + " is too long (max " + (int) d.max() + ").");
                if (v.length() < d.min()) throw new IllegalArgumentException(d.label() + " is required.");
                if (v.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException(d.label() + " has invalid characters.");
                if (d.key().equals("shiprocket.pickupPostcode") && !v.isEmpty() && !v.matches("[1-9][0-9]{5}"))
                    throw new IllegalArgumentException("Pickup pincode must be 6 digits.");
                if (d.key().equals("business.whatsapp") && !v.isEmpty() && !v.matches("[0-9+ ]{8,20}"))
                    throw new IllegalArgumentException("WhatsApp number may contain only digits, + and spaces.");
                return v;
            }
            case EMAIL -> {
                if (!v.isEmpty() && !Validators.email(v)) throw new IllegalArgumentException(d.label() + " is not a valid email.");
                return v.toLowerCase();
            }
            case BOOL -> {
                if (!v.equals("true") && !v.equals("false")) throw new IllegalArgumentException(d.label() + " must be true or false.");
                return v;
            }
            case INT -> {
                long n;
                try { n = Long.parseLong(v); } catch (NumberFormatException e) { throw new IllegalArgumentException(d.label() + " must be a whole number."); }
                if (n < d.min() || n > d.max()) throw new IllegalArgumentException(d.label() + " must be between " + (long) d.min() + " and " + (long) d.max() + ".");
                return Long.toString(n);
            }
            case MONEY, DECIMAL -> {
                BigDecimal n;
                try { n = new BigDecimal(v); } catch (NumberFormatException e) { throw new IllegalArgumentException(d.label() + " must be a number."); }
                if (n.compareTo(BigDecimal.valueOf(d.min())) < 0 || n.compareTo(BigDecimal.valueOf(d.max())) > 0)
                    throw new IllegalArgumentException(d.label() + " must be between " + d.min() + " and " + d.max() + ".");
                return n.setScale(d.type() == Type.MONEY ? 2 : 3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            }
        }
        throw new IllegalStateException();
    }
}
