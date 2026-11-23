package uz.osonmarket.catalog.social;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface ProductCommentRepository extends JpaRepository<ProductComment, UUID> {
    @EntityGraph(attributePaths = "author")
    Page<ProductComment> findByProductIdAndParentIsNull(UUID product, Pageable page);

    @EntityGraph(attributePaths = "author")
    Page<ProductComment> findByProductIdAndParentId(UUID product, UUID parent, Pageable page);

    boolean existsByIdAndProductId(UUID id, UUID product);
    long countByProductIdAndDeletedAtIsNull(UUID product);
    long countByParentId(UUID parent);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProductComment c where c.id = :id and c.product.id = :product")
    Optional<ProductComment> lock(@Param("id") UUID id, @Param("product") UUID product);
}
