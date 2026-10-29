package in.trendsfit.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchanges")
public class Exchange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "order_id", nullable = false) public Long orderId;
    @Column(name = "order_item_id", nullable = false) public Long orderItemId;
    @Column(name = "new_variant_id", nullable = false) public Long newVariantId;
    @Column(nullable = false) public String reason;
    public String description;
    @Column(nullable = false) public String status = "PENDING";
    @Column(name = "admin_note") public String adminNote;
    @Column(name = "created_at", insertable = false, updatable = false) public LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) public LocalDateTime updatedAt;
}
