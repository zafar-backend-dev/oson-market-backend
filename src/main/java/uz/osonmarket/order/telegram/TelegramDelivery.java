package uz.osonmarket.order.telegram;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_telegram_deliveries", indexes = @Index(columnList = "next_attempt_at"))
class TelegramDelivery {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(nullable = false)
    Long chatId;
    @Column(nullable = false, columnDefinition = "text")
    String message;
    int attempts;
    @Column(name = "next_attempt_at")
    Instant nextAttemptAt;
    Instant sentAt;
}
