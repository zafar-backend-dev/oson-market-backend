package uz.osonmarket.catalog.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.osonmarket.domain.catalog.ProductType;

public interface ProductTypeRepository extends JpaRepository<ProductType, UUID> {
    Optional<ProductType> findBySku(String sku);

    Optional<ProductType> findByBarcode(String barcode);

    List<ProductType> findByProductIdOrderByIdAsc(UUID productId);
}
