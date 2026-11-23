package uz.osonmarket.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_emails", indexes = @Index(columnList = "next_attempt_at"))
class OrderStatusEmail {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    String recipient;
    String language;
    String orderNumber;
    @Enumerated(EnumType.STRING)
    OrderStatus status;
    @Column(length = 1000)
    String reason;
    int attempts;
    Instant nextAttemptAt;
    Instant sentAt;
}
