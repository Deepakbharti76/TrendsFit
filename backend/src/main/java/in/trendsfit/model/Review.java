package in.trendsfit.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "product_id", nullable = false) public Long productId;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(nullable = false) public int rating;
    public String title;
    public String body;
    @Column(name = "verified_purchase") public boolean verifiedPurchase;
    @Column(nullable = false) public String status = "PENDING";
    @Column(name = "created_at", insertable = false, updatable = false) public LocalDateTime createdAt;
}
