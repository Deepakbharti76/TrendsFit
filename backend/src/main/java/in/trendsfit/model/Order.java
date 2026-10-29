package in.trendsfit.model;

import in.trendsfit.domain.OrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "PurchaseOrder")
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "order_number", nullable = false, unique = true) public String orderNumber;
    @Column(name = "user_id") public Long userId;
    @Column(name = "customer_name", nullable = false) public String customerName;
    @Column(nullable = false) public String email;
    @Column(nullable = false) public String phone;
    @Column(name = "ship_line1", nullable = false) public String shipLine1;
    @Column(name = "ship_line2") public String shipLine2;
    @Column(name = "ship_city", nullable = false) public String shipCity;
    @Column(name = "ship_state", nullable = false) public String shipState;
    @Column(name = "ship_pincode", nullable = false) public String shipPincode;
    @Column(nullable = false) public String status = "PENDING_PAYMENT";
    @Column(name = "payment_method", nullable = false) public String paymentMethod;
    @Column(name = "payment_status", nullable = false) public String paymentStatus = "PENDING";
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal subtotal;
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal shipping = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal total;
    @Column(name = "coupon_code") public String couponCode;
    @Column(name = "idempotency_key") public String idempotencyKey;
    @Column(name = "access_token", nullable = false) public String accessToken;
    @Column(name = "delivered_at") public LocalDateTime deliveredAt;
    @Column(name = "stock_held") public boolean stockHeld = true;
    @Column(name = "created_at", insertable = false, updatable = false) public LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) public LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id ASC")
    public List<OrderItem> items = new ArrayList<>();

    public OrderStatus statusEnum() { return OrderStatus.valueOf(status); }
}
