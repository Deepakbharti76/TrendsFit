package in.trendsfit.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "order_id", nullable = false) public Long orderId;
    @Column(nullable = false) public String kind = "FORWARD";
    @Column(name = "exchange_id") public Long exchangeId;
    @Column(name = "sr_order_id") public String srOrderId;
    @Column(name = "sr_shipment_id") public String srShipmentId;
    public String awb;
    public String courier;
    @Column(name = "label_url") public String labelUrl;
    @Column(nullable = false) public String status = "CREATED";
    @Column(name = "status_text") public String statusText;
    @Column(name = "tracking_url") public String trackingUrl;
    @Column(name = "estimated_delivery") public LocalDate estimatedDelivery;
    @Column(name = "last_event_at") public LocalDateTime lastEventAt;
    @Column(name = "created_at", insertable = false, updatable = false) public LocalDateTime createdAt;
}
