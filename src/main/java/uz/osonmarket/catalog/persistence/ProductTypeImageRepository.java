package uz.osonmarket.catalog.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.osonmarket.domain.catalog.ProductTypeImage;

public interface ProductTypeImageRepository extends JpaRepository<ProductTypeImage, UUID> {
    List<ProductTypeImage> findByProductTypeIdOrderBySortOrderAscIdAsc(UUID productTypeId);
}
