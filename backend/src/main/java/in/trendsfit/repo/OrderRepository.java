package in.trendsfit.repo;

import in.trendsfit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByIdempotencyKey(String key);
    List<Order> findByUserIdOrderByIdDesc(Long userId);
}
