package uz.osonmarket.catalog.social;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductCommentLikeRepository extends JpaRepository<ProductCommentLike, UUID> {
    Optional<ProductCommentLike> findByCommentIdAndUserId(UUID comment, UUID user);
    long countByCommentId(UUID comment);
    void deleteByCommentId(UUID comment);
}
