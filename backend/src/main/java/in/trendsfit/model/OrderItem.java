package in.trendsfit.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") public Order order;
    @Column(name = "product_id", nullable = false) public Long productId;
    @Column(name = "variant_id", nullable = false) public Long variantId;
    @Column(nullable = false) public String name;
    @Column(nullable = false) public String sku;
    @Column(nullable = false) public String size;
    @Column(nullable = false) public String color;
    @Column(name = "image_url") public String imageUrl;
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2) public BigDecimal unitPrice;
    @Column(nullable = false) public int qty;
    @Column(name = "line_total", nullable = false, precision = 10, scale = 2) public BigDecimal lineTotal;
}
