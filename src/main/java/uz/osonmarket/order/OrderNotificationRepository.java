package uz.osonmarket.order;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface OrderNotificationRepository extends JpaRepository<OrderNotification, UUID> {
    @EntityGraph(attributePaths = "order")
    Page<OrderNotification> findByUserId(UUID userId, Pageable pageable);
    long countByUserIdAndReadAtIsNull(UUID userId);
    boolean existsByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update OrderNotification n set n.readAt = :now where n.id = :id and n.userId = :owner and n.readAt is null")
    int markRead(@Param("id") UUID id, @Param("owner") UUID owner, @Param("now") Instant now);
}
