package uz.osonmarket.order.telegram;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "order_telegram_recipients")
class TelegramRecipient {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(nullable = false, unique = true)
    Long chatId;
    @Column(length = 100)
    String label;
}
