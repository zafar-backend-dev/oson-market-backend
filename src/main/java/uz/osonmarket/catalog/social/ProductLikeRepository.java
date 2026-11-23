package uz.osonmarket.catalog.social;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductLikeRepository extends JpaRepository<ProductLike, UUID> {
    Optional<ProductLike> findByProductIdAndUserId(UUID product, UUID user);
    long countByProductId(UUID product);
}
