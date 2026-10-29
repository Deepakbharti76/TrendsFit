package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Jwt;
import in.trendsfit.domain.Role;
import in.trendsfit.domain.Validators;
import in.trendsfit.model.User;
import in.trendsfit.model.VerificationToken;
import in.trendsfit.repo.UserRepository;
import in.trendsfit.repo.VerificationTokenRepository;
import in.trendsfit.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final int MAX_FAILS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository users;
    private final VerificationTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final AppConfig cfg;
    private final EmailService mail;
    private final AuditService audit;
    private final Clock clock;

    public AuthService(UserRepository users, VerificationTokenRepository tokens, PasswordEncoder encoder, AppConfig cfg,
                       EmailService mail, AuditService audit, Clock clock) {
        this.users = users; this.tokens = tokens; this.encoder = encoder; this.cfg = cfg;
        this.mail = mail; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public Map<String, Object> register(String fullName, String email, String phone, String password, boolean marketing) {
        Map<String, String> errs = new LinkedHashMap<>();
        if (fullName == null || fullName.trim().length() < 2) errs.put("fullName", "Enter your full name.");
        if (!Validators.email(email)) errs.put("email", "Enter a valid email address.");
        String mobile = null;
        if (phone == null || phone.isBlank()) {
            errs.put("phone", "Mobile number is required.");
        } else {
            mobile = Validators.mobileExact(phone);
            if (mobile == null) errs.put("phone", "Enter a valid 10-digit mobile number (digits only, no spaces or +91).");
        }
        List<String> pw = Validators.passwordProblems(password, email);
        if (!pw.isEmpty()) errs.put("password", String.join(" ", pw));
        if (!errs.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Please correct the highlighted fields.", errs);
        String em = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(em))
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "An account with this email already exists. Try logging in.");
        User u = new User();
        u.email = em;
        u.fullName = fullName.trim();
        u.phone = mobile;
        u.passwordHash = encoder.encode(password);
        u.role = Role.CUSTOMER.name();
        u.marketingConsent = marketing;
        u = users.save(u);
        String raw = issueToken(u.id, "VERIFY_EMAIL", 60 * 24);
        mail.sendVerification(u.email, u.fullName, raw);
        return session(u);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> login(String email, String password) {
        Optional<User> found = email == null ? Optional.empty() : users.findByEmailIgnoreCase(email.trim());
        LocalDateTime now = LocalDateTime.now(clock);
        if (found.isEmpty()) {
            encoder.matches(password == null ? "" : password, encoder.encode("dummy-password-for-timing"));
            throw bad();
        }
        User u = found.get();
        if (u.lockedUntil != null && u.lockedUntil.isAfter(now))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED", "Too many failed attempts. Try again in a few minutes or reset your password.");
        if (!u.enabled || password == null || !encoder.matches(password, u.passwordHash)) {
            u.failedLogins++;
            if (u.failedLogins >= MAX_FAILS) { u.lockedUntil = now.plusMinutes(LOCK_MINUTES); u.failedLogins = 0; }
            users.save(u);
            throw bad();
        }
        u.failedLogins = 0;
        u.lockedUntil = null;
        users.save(u);
        if (u.roleEnum().isStaff()) audit.logAs(u.email, "ADMIN_LOGIN", "user", u.id, null);
        return session(u);
    }

    private ApiException bad() { return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Incorrect email or password."); }

    @Transactional
    public void verifyEmail(String raw) {
        VerificationToken t = validToken(raw, "VERIFY_EMAIL");
        User u = users.findById(t.userId).orElseThrow(() -> ApiException.notFound("Account not found."));
        u.emailVerified = true;
        users.save(u);
        t.usedAt = LocalDateTime.now(clock);
        tokens.save(t);
    }

    /** Always succeeds from the caller's point of view so account existence is not revealed. */
    @Transactional
    public void forgotPassword(String email) {
        if (email == null) return;
        users.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
            String raw = issueToken(u.id, "RESET_PASSWORD", 60);
            mail.sendReset(u.email, raw);
        });
    }

    @Transactional
    public void resetPassword(String raw, String newPassword) {
        VerificationToken t = validToken(raw, "RESET_PASSWORD");
        User u = users.findById(t.userId).orElseThrow(() -> ApiException.notFound("Account not found."));
        List<String> pw = Validators.passwordProblems(newPassword, u.email);
        if (!pw.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", String.join(" ", pw), Map.of("password", String.join(" ", pw)));
        u.passwordHash = encoder.encode(newPassword);
        u.failedLogins = 0;
        u.lockedUntil = null;
        users.save(u);
        t.usedAt = LocalDateTime.now(clock);
        tokens.save(t);
    }

    @Transactional
    public void resendVerification(long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Account not found."));
        if (u.emailVerified) return;
        mail.sendVerification(u.email, u.fullName, issueToken(u.id, "VERIFY_EMAIL", 60 * 24));
    }

    private String issueToken(long userId, String type, int minutes) {
        String raw = Tokens.random(32);
        VerificationToken t = new VerificationToken();
        t.userId = userId;
        t.type = type;
        t.tokenHash = Tokens.sha256(raw);
        t.expiresAt = LocalDateTime.now(clock).plusMinutes(minutes);
        tokens.save(t);
        return raw;
    }

    private VerificationToken validToken(String raw, String type) {
        if (raw == null || raw.isBlank()) throw ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired.");
        VerificationToken t = tokens.findByTokenHashAndType(Tokens.sha256(raw), type)
            .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired."));
        if (t.usedAt != null || t.expiresAt.isBefore(LocalDateTime.now(clock)))
            throw ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired.");
        return t;
    }

    public Map<String, Object> session(User u) {
        if (cfg.jwtSecret == null || cfg.jwtSecret.length() < 32)
            throw ApiException.unavailable("Server is not configured for sign-in (JWT secret missing).");
        String token = Jwt.issue(cfg.jwtSecret, u.email, u.role, u.id, cfg.jwtTtlMinutes * 60, Instant.now(clock));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("user", profile(u));
        return m;
    }

    public static Map<String, Object> profile(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id);
        m.put("email", u.email);
        m.put("fullName", u.fullName);
        m.put("phone", u.phone);
        m.put("role", u.role);
        m.put("emailVerified", u.emailVerified);
        m.put("staff", u.roleEnum().isStaff());
        List<String> perms = u.roleEnum().permissions().stream().map(Enum::name).sorted().toList();
        m.put("permissions", perms);
        return m;
    }
}
