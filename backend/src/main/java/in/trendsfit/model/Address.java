package in.trendsfit.model;

import jakarta.persistence.*;

@Entity
@Table(name = "addresses")
public class Address {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id", nullable = false) public Long userId;
    public String label = "HOME";
    @Column(name = "full_name", nullable = false) public String fullName;
    @Column(nullable = false) public String phone;
    @Column(nullable = false) public String line1;
    public String line2;
    @Column(nullable = false) public String city;
    @Column(nullable = false) public String state;
    @Column(nullable = false) public String pincode;
    @Column(name = "is_default") public boolean isDefault;
}
