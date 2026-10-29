package in.trendsfit.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "order_id", nullable = false) public Long orderId;
    @Column(nullable = false) public String provider = "RAZORPAY";
    @Column(name = "provider_order_id", nullable = false, unique = true) public String providerOrderId;
    @Column(name = "provider_payment_id", unique = true) public String providerPaymentId;
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal amount;
    @Column(nullable = false) public String currency = "INR";
    @Column(nullable = false) public String status = "CREATED";
    @Column(name = "failure_reason") public String failureReason;
    @Column(name = "created_at", insertable = false, updatable = false) public LocalDateTime createdAt;
}
