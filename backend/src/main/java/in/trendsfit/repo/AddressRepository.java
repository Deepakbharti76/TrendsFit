package in.trendsfit.repo;

import in.trendsfit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByIsDefaultDescIdDesc(Long userId);
    Optional<Address> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
}
