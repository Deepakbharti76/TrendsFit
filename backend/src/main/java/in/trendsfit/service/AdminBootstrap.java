package in.trendsfit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Role;
import in.trendsfit.domain.Validators;
import in.trendsfit.model.User;
import in.trendsfit.repo.UserRepository;

/**
 * Creates the first Super Admin from environment variables (TRENDSFIT_ADMIN_BOOTSTRAP_EMAIL / _PASSWORD) when no staff exists.
 * Nothing is created when the variables are empty. The password is never logged.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final AppConfig cfg;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminBootstrap(AppConfig cfg, UserRepository users, PasswordEncoder encoder) {
        this.cfg = cfg; this.users = users; this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cfg.adminBootstrapEmail.isBlank() || cfg.adminBootstrapPassword.isBlank()) {
            log.info("Admin bootstrap skipped (no bootstrap credentials configured).");
            return;
        }
        boolean validEmail = Validators.email(cfg.adminBootstrapEmail);
var passwordErrors = Validators.passwordProblems(
        cfg.adminBootstrapPassword,
        cfg.adminBootstrapEmail
);

log.info("Admin email valid: {}", validEmail);
log.info("Admin password rule errors: {}", passwordErrors);

if (!validEmail || !passwordErrors.isEmpty()) {
    log.warn("Admin bootstrap skipped because validation failed.");
    return;
}
        String email = cfg.adminBootstrapEmail.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) return;
        User u = new User();
        u.email = email;
        u.fullName = "Store Admin";
        u.passwordHash = encoder.encode(cfg.adminBootstrapPassword);
        u.role = Role.SUPER_ADMIN.name();
        u.emailVerified = true;
        users.save(u);
        log.info("Created bootstrap Super Admin {}. Remove the bootstrap variables and change the password after first login.", email);
    }
}
