package in.trendsfit.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens")
public class VerificationToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(name = "token_hash", nullable = false) public String tokenHash;
    @Column(nullable = false) public String type;
    @Column(name = "expires_at", nullable = false) public LocalDateTime expiresAt;
    @Column(name = "used_at") public LocalDateTime usedAt;
}
