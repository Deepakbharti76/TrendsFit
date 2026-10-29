package in.trendsfit.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
public class Coupon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true) public String code;
    @Column(nullable = false) public String type;
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal value;
    @Column(name = "min_order", nullable = false, precision = 10, scale = 2) public BigDecimal minOrder = BigDecimal.ZERO;
    @Column(name = "max_discount", precision = 10, scale = 2) public BigDecimal maxDiscount;
    @Column(name = "starts_at") public LocalDateTime startsAt;
    @Column(name = "expires_at") public LocalDateTime expiresAt;
    @Column(name = "max_uses") public Integer maxUses;
    @Column(name = "per_user_limit") public Integer perUserLimit;
    @Column(name = "used_count") public int usedCount;
    public boolean active = true;
    @Column(name = "created_at", insertable = false, updatable = false) public LocalDateTime createdAt;
}
