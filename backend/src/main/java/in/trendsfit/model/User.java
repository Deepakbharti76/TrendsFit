package in.trendsfit.model;

import in.trendsfit.domain.Role;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true) public String email;
    public String phone;
    @Column(name = "password_hash", nullable = false) public String passwordHash;
    @Column(name = "full_name", nullable = false) public String fullName;
    @Column(nullable = false) public String role = "CUSTOMER";
    @Column(name = "email_verified") public boolean emailVerified;
    public boolean enabled = true;
    @Column(name = "marketing_consent") public boolean marketingConsent;
    @Column(name = "failed_logins") public int failedLogins;
    @Column(name = "locked_until") public LocalDateTime lockedUntil;
    @Column(name = "created_at", insertable = false, updatable = false) public LocalDateTime createdAt;

    public Role roleEnum() { return Role.valueOf(role); }
}
