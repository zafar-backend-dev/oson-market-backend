package uz.osonmarket.advertising.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advertisement_media", indexes = @Index(columnList = "advertisement_id"))
class AdvertisementMedia {
    @Id UUID id;
    @Column(nullable = false) UUID advertisementId;
    @Column(nullable = false, length = 255) String filename;
    @Column(nullable = false, length = 64) String contentType;
    @Column(nullable = false) long sizeBytes;
    @Column(nullable = false) Instant createdAt;
}
