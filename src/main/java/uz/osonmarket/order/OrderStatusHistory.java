package uz.osonmarket.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
class OrderStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(name = "order_id", nullable = false)
    UUID orderId;
    @Column(name = "changed_by")
    UUID actorId;
    @Column(length = 32)
    String oldStatus;
    @Column(length = 32)
    String newStatus;
    @Column(length = 1000)
    String note;
    Instant createdAt;
}
