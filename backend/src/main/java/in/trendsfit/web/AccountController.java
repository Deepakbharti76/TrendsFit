package in.trendsfit.web;

import in.trendsfit.security.CurrentUser;
import in.trendsfit.service.AccountService;
import in.trendsfit.service.OrderService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Everything here is scoped to the authenticated user (the id comes from the verified token, never from the request). */
@RestController
@RequestMapping("/api/me")
public class AccountController {
    private final AccountService account;
    private final OrderService orders;

    public AccountController(AccountService account, OrderService orders) { this.account = account; this.orders = orders; }

    @GetMapping
    public Map<String, Object> me() { return account.me(CurrentUser.require().id()); }

    public record ProfileReq(String fullName, String phone, Boolean marketingConsent) {}

    @PutMapping
    public Map<String, Object> update(@RequestBody ProfileReq r) {
        return account.updateProfile(CurrentUser.require().id(), r.fullName(), r.phone(), r.marketingConsent());
    }

    public record PasswordReq(String currentPassword, String newPassword) {}

    @PostMapping("/password")
    public Map<String, Object> password(@RequestBody PasswordReq r) {
        account.changePassword(CurrentUser.require().id(), r.currentPassword(), r.newPassword());
        return Map.of("message", "Password changed.");
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> myOrders() { return orders.listForUser(CurrentUser.require().id()); }

    @GetMapping("/addresses")
    public List<Map<String, Object>> addresses() { return account.addresses(CurrentUser.require().id()); }

    @PostMapping("/addresses")
    public Map<String, Object> addAddress(@RequestBody AccountService.AddressReq r) { return account.saveAddress(CurrentUser.require().id(), null, r); }

    @PutMapping("/addresses/{id}")
    public Map<String, Object> editAddress(@PathVariable long id, @RequestBody AccountService.AddressReq r) {
        return account.saveAddress(CurrentUser.require().id(), id, r);
    }

    @DeleteMapping("/addresses/{id}")
    public Map<String, Object> deleteAddress(@PathVariable long id) {
        account.deleteAddress(CurrentUser.require().id(), id);
        return Map.of("ok", true);
    }

    @GetMapping("/wishlist")
    public List<Map<String, Object>> wishlist() { return account.wishlist(CurrentUser.require().id()); }

    @GetMapping("/wishlist/ids")
    public List<Long> wishlistIds() { return account.wishlistIds(CurrentUser.require().id()); }

    @PostMapping("/wishlist/{productId}")
    public Map<String, Object> addWish(@PathVariable long productId) {
        account.addWishlist(CurrentUser.require().id(), productId);
        return Map.of("ok", true);
    }

    @DeleteMapping("/wishlist/{productId}")
    public Map<String, Object> removeWish(@PathVariable long productId) {
        account.removeWishlist(CurrentUser.require().id(), productId);
        return Map.of("ok", true);
    }

    @PostMapping("/reviews/{productSlug}")
    public Map<String, Object> review(@PathVariable String productSlug, @RequestBody AccountService.ReviewReq r) {
        return account.review(CurrentUser.require().id(), productSlug, r);
    }
}
