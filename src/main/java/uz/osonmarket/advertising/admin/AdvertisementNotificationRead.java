package uz.osonmarket.advertising.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advertisement_notification_reads")
@IdClass(AdvertisementNotificationRead.Key.class)
class AdvertisementNotificationRead {
    @Id UUID userId;
    @Id UUID dispatchId;
    @Column(nullable = false) Instant readAt;

    record Key(UUID userId, UUID dispatchId) implements java.io.Serializable {}
}
