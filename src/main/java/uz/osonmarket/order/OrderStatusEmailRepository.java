package uz.osonmarket.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface OrderStatusEmailRepository extends JpaRepository<OrderStatusEmail, UUID> {
    List<OrderStatusEmail> findTop20ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OrderStatusEmail e where e.id = :id")
    Optional<OrderStatusEmail> lock(@Param("id") UUID id);
}
