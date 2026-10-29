package in.trendsfit.repo;

import in.trendsfit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByProviderOrderId(String providerOrderId);
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);
}
