package in.trendsfit.model;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true) public String slug;
    @Column(nullable = false) public String name;
    public String description;
    @Column(name = "sort_order") public int sortOrder;
    public boolean active = true;
}
