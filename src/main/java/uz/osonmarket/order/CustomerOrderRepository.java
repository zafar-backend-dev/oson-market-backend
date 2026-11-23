package uz.osonmarket.order;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    Optional<CustomerOrder> findByIdAndUserId(UUID id, UUID userId);
    Optional<CustomerOrder> findByUserIdAndRequestId(UUID userId, UUID requestId);
    Page<CustomerOrder> findByUserId(UUID userId, Pageable pageable);

    @Query("""
            select o from CustomerOrder o
            where (:status is null or o.status = :status)
              and (:number is null or upper(o.orderNumber) = :number)
            """)
    Page<CustomerOrder> findForAdmin(@Param("status") String status, @Param("number") String number, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id and o.user.id = :owner")
    Optional<CustomerOrder> findOwnedForUpdate(@Param("id") UUID id, @Param("owner") UUID owner);
}
