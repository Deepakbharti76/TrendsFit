package in.trendsfit.repo;

import in.trendsfit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);
    @Query("select v from ProductVariant v join fetch v.product where v.id in :ids")
    List<ProductVariant> findAllWithProduct(@Param("ids") Collection<Long> ids);
}
