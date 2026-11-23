package uz.osonmarket.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_notifications", indexes = @Index(columnList = "user_id, created_at"))
class OrderNotification {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(nullable = false)
    UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    CustomerOrder order;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    OrderStatus status;
    @Column(length = 1000)
    String reason;
    @Column(nullable = false)
    Instant createdAt;
    Instant readAt;
}
