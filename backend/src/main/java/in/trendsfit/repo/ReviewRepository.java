package in.trendsfit.repo;

import in.trendsfit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdAndStatusOrderByIdDesc(Long productId, String status);
    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);
    List<Review> findByStatusOrderByIdDesc(String status);
    List<Review> findByUserIdOrderByIdDesc(Long userId);
}
