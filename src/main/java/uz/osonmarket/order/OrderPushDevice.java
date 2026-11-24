package uz.osonmarket.order;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "order_push_devices", indexes = @Index(columnList = "user_id"))
class OrderPushDevice {
    @Id @Column(length = 512) String token;
    @Column(nullable = false) UUID userId;
    @Column(nullable = false, length = 8) String language;
}
