package in.trendsfit.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") public Product product;
    @Column(nullable = false, unique = true) public String sku;
    @Column(nullable = false) public String size;
    @Column(nullable = false) public String color;
    @Column(name = "color_hex") public String colorHex;
    public int stock;
    @Column(name = "price_override", precision = 10, scale = 2) public BigDecimal priceOverride;
    public boolean active = true;
}
