package in.trendsfit.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true) public String slug;
    @Column(nullable = false) public String name;
    @Column(nullable = false, unique = true) public String sku;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id") public Category category;
    @Column(columnDefinition = "TEXT") public String description;
    public String fit;
    public String material;
    @Column(columnDefinition = "TEXT") public String care;
    @Column(name = "size_chart", columnDefinition = "TEXT") public String sizeChart;
    @Column(name = "video_url") public String videoUrl;
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal price;
    @Column(name = "sale_price", precision = 10, scale = 2) public BigDecimal salePrice;
    @Column(name = "seo_title") public String seoTitle;
    @Column(name = "seo_description") public String seoDescription;
    @Column(nullable = false) public String status = "DRAFT";
    @Column(name = "is_featured") public boolean featured;
    @Column(name = "is_new_arrival") public boolean newArrival;
    @Column(name = "is_bestseller") public boolean bestseller;
    @Column(name = "is_demo") public boolean demo;
    @Column(name = "rating_avg", precision = 3, scale = 2) public BigDecimal ratingAvg = BigDecimal.ZERO;
    @Column(name = "rating_count") public int ratingCount;
    @Column(name = "sold_count") public int soldCount;
    @Column(name = "created_at", insertable = false, updatable = false) public LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) public LocalDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("sortOrder ASC, id ASC")
    public List<ProductImage> images = new ArrayList<>();
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id ASC")
    public List<ProductVariant> variants = new ArrayList<>();

    public boolean isActive() { return "ACTIVE".equals(status); }
}
