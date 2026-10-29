package in.trendsfit.web;

import in.trendsfit.security.CurrentUser;
import in.trendsfit.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    public record RegisterReq(String fullName, String email, String phone, String password, Boolean marketingConsent) {}
    public record LoginReq(String email, String password) {}
    public record TokenReq(String token) {}
    public record EmailReq(String email) {}
    public record ResetReq(String token, String password) {}

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterReq r) {
        return auth.register(r.fullName(), r.email(), r.phone(), r.password(), Boolean.TRUE.equals(r.marketingConsent()));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq r) { return auth.login(r.email(), r.password()); }

    @PostMapping("/verify-email")
    public Map<String, Object> verify(@RequestBody TokenReq r) {
        auth.verifyEmail(r.token());
        return Map.of("message", "Email verified.");
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgot(@RequestBody EmailReq r) {
        auth.forgotPassword(r.email());
        return Map.of("message", "If an account exists for that email, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public Map<String, Object> reset(@RequestBody ResetReq r) {
        auth.resetPassword(r.token(), r.password());
        return Map.of("message", "Password updated. You can log in now.");
    }

    /** Requires a valid token even though the path is under /api/auth. */
    @PostMapping("/resend-verification")
    public Map<String, Object> resend() {
        auth.resendVerification(CurrentUser.require().id());
        return Map.of("message", "Verification email sent.");
    }
}
