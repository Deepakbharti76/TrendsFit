package in.trendsfit.repo;

import in.trendsfit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ExchangeRepository extends JpaRepository<Exchange, Long> {
    List<Exchange> findByOrderIdOrderByIdDesc(Long orderId);
    boolean existsByOrderItemIdAndStatusNot(Long orderItemId, String status);
    List<Exchange> findAllByOrderByIdDesc();
}
