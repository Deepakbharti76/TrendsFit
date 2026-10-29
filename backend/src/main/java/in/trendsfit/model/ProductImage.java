package in.trendsfit.model;

import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") public Product product;
    @Column(nullable = false) public String url;
    public String alt;
    @Column(name = "sort_order") public int sortOrder;
}
