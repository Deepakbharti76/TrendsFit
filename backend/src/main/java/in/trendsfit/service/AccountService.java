package in.trendsfit.service;

import in.trendsfit.domain.Validators;
import in.trendsfit.model.Address;
import in.trendsfit.model.Review;
import in.trendsfit.model.User;
import in.trendsfit.repo.AddressRepository;
import in.trendsfit.repo.ReviewRepository;
import in.trendsfit.repo.UserRepository;
import in.trendsfit.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer account: profile, password, address book, wishlist and reviews. Every query is scoped to the logged-in user. */
@Service
public class AccountService {
    private final UserRepository users;
    private final AddressRepository addresses;
    private final ReviewRepository reviews;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final CatalogService catalog;

    public AccountService(UserRepository users, AddressRepository addresses, ReviewRepository reviews, PasswordEncoder encoder,
                          JdbcTemplate jdbc, CatalogService catalog) {
        this.users = users; this.addresses = addresses; this.reviews = reviews; this.encoder = encoder; this.jdbc = jdbc; this.catalog = catalog;
    }

    public Map<String, Object> me(long userId) {
        return AuthService.profile(users.findById(userId).orElseThrow(() -> ApiException.notFound("Account not found.")));
    }

    @Transactional
    public Map<String, Object> updateProfile(long userId, String fullName, String phone, Boolean marketing) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Account not found."));
        Map<String, String> errs = new LinkedHashMap<>();
        if (fullName != null) {
            if (fullName.trim().length() < 2) errs.put("fullName", "Enter your full name.");
            else u.fullName = fullName.trim();
        }
        if (phone != null) {
            if (phone.isBlank()) u.phone = null;
            else {
                String mobile = Validators.mobile(phone);
                if (mobile == null) errs.put("phone", "Enter a valid 10-digit mobile number.");
                else u.phone = mobile;
            }
        }
        if (!errs.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Please correct the highlighted fields.", errs);
        if (marketing != null) u.marketingConsent = marketing;
        return AuthService.profile(users.save(u));
    }

    @Transactional
    public void changePassword(long userId, String current, String next) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Account not found."));
        if (current == null || !encoder.matches(current, u.passwordHash))
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_CREDENTIALS", "Your current password is incorrect.");
        List<String> p = Validators.passwordProblems(next, u.email);
        if (!p.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", String.join(" ", p), Map.of("password", String.join(" ", p)));
        u.passwordHash = encoder.encode(next);
        users.save(u);
    }

    // ---------------------------------------------------------------- addresses

    public record AddressReq(String label, String fullName, String phone, String line1, String line2, String city, String state, String pincode, Boolean isDefault) {}

    public List<Map<String, Object>> addresses(long userId) {
        return addresses.findByUserIdOrderByIsDefaultDescIdDesc(userId).stream().map(AccountService::addressView).toList();
    }

    static Map<String, Object> addressView(Address a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("label", a.label);
        m.put("fullName", a.fullName);
        m.put("phone", a.phone);
        m.put("line1", a.line1);
        m.put("line2", a.line2);
        m.put("city", a.city);
        m.put("state", a.state);
        m.put("pincode", a.pincode);
        m.put("isDefault", a.isDefault);
        return m;
    }

    @Transactional
    public Map<String, Object> saveAddress(long userId, Long id, AddressReq r) {
        Map<String, String> e = new LinkedHashMap<>();
        if (r.fullName() == null || r.fullName().trim().length() < 2) e.put("fullName", "Enter the receiver's name.");
        if (r.phone() == null || r.phone().isBlank()) e.put("phone", "Mobile number is required.");
        else if (Validators.mobileExact(r.phone()) == null) e.put("phone", "Enter a valid 10-digit mobile number (digits only, no spaces or +91).");
        if (r.line1() == null || r.line1().isBlank()) e.put("line1", "Enter the address.");
        if (r.city() == null || r.city().isBlank()) e.put("city", "Enter the city.");
        if (r.state() == null || r.state().isBlank()) e.put("state", "Enter the state.");
        if (!Validators.pincode(r.pincode())) e.put("pincode", "Enter a valid 6-digit pincode.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Please correct the highlighted fields.", e);
        Address a;
        if (id == null) {
            if (addresses.countByUserId(userId) >= 10) throw ApiException.badRequest("LIMIT", "You can save up to 10 addresses.");
            a = new Address();
            a.userId = userId;
        } else {
            a = addresses.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Address not found."));
        }
        a.label = r.label() == null || r.label().isBlank() ? "HOME" : r.label().trim().toUpperCase();
        a.fullName = r.fullName().trim();
        a.phone = Validators.mobileExact(r.phone());
        a.line1 = r.line1().trim();
        a.line2 = r.line2() == null || r.line2().isBlank() ? null : r.line2().trim();
        a.city = r.city().trim();
        a.state = r.state().trim();
        a.pincode = r.pincode().trim();
        boolean first = addresses.countByUserId(userId) == 0 && id == null;
        if (Boolean.TRUE.equals(r.isDefault()) || first) {
            jdbc.update("UPDATE addresses SET is_default = 0 WHERE user_id = ?", userId);
            a.isDefault = true;
        }
        return addressView(addresses.save(a));
    }

    @Transactional
    public void deleteAddress(long userId, long id) {
        Address a = addresses.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Address not found."));
        addresses.delete(a);
    }

    // ---------------------------------------------------------------- wishlist

    public List<Map<String, Object>> wishlist(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.slug, p.name, p.price, p.sale_price, p.rating_avg, p.rating_count, p.is_new_arrival, p.is_bestseller, p.fit, "
            + "c.slug AS category_slug, c.name AS category_name FROM wishlist_items w JOIN products p ON p.id=w.product_id JOIN categories c ON c.id=p.category_id "
            + "WHERE w.user_id = ? AND p.status = 'ACTIVE' ORDER BY w.created_at DESC", userId);
        return catalog.cards(rows);
    }

    public List<Long> wishlistIds(long userId) {
        return jdbc.queryForList("SELECT product_id FROM wishlist_items WHERE user_id = ?", Long.class, userId);
    }

    public void addWishlist(long userId, long productId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ? AND status = 'ACTIVE'", Integer.class, productId);
        if (n == null || n == 0) throw ApiException.notFound("Product not found.");
        jdbc.update("INSERT IGNORE INTO wishlist_items (user_id, product_id) VALUES (?, ?)", userId, productId);
    }

    public void removeWishlist(long userId, long productId) {
        jdbc.update("DELETE FROM wishlist_items WHERE user_id = ? AND product_id = ?", userId, productId);
    }

    // ---------------------------------------------------------------- reviews

    public record ReviewReq(Integer rating, String title, String body) {}

    /** One review per product per customer; goes to moderation. "Verified purchase" is computed from real delivered orders. */
    @Transactional
    public Map<String, Object> review(long userId, String productSlug, ReviewReq r) {
        if (r == null || r.rating() == null || r.rating() < 1 || r.rating() > 5) throw ApiException.badRequest("BAD_RATING", "Choose a rating from 1 to 5.");
        Long pid;
        try {
            pid = jdbc.queryForObject("SELECT id FROM products WHERE slug = ? AND status = 'ACTIVE'", Long.class, productSlug);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("Product not found.");
        }
        if (reviews.findByProductIdAndUserId(pid, userId).isPresent()) throw ApiException.conflict("REVIEW_EXISTS", "You have already reviewed this product.");
        Integer bought = jdbc.queryForObject(
            "SELECT COUNT(*) FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.user_id = ? AND i.product_id = ? AND o.delivered_at IS NOT NULL", Integer.class, userId, pid);
        Review v = new Review();
        v.productId = pid;
        v.userId = userId;
        v.rating = r.rating();
        v.title = r.title() == null ? null : (r.title().length() > 120 ? r.title().substring(0, 120) : r.title().trim());
        v.body = r.body() == null ? null : (r.body().length() > 2000 ? r.body().substring(0, 2000) : r.body().trim());
        v.verifiedPurchase = bought != null && bought > 0;
        v.status = "PENDING";
        reviews.save(v);
        return Map.of("status", "PENDING", "message", "Thanks! Your review will appear after moderation.");
    }

    /** Recalculates the stored rating of a product from approved reviews (called after moderation). */
    public void refreshRating(long productId) {
        jdbc.update("UPDATE products SET rating_avg = COALESCE((SELECT ROUND(AVG(rating), 2) FROM reviews WHERE product_id = ? AND status = 'APPROVED'), 0), "
            + "rating_count = (SELECT COUNT(*) FROM reviews WHERE product_id = ? AND status = 'APPROVED') WHERE id = ?", productId, productId, productId);
    }
}
