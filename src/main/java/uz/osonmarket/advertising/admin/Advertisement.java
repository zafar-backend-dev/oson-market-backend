package uz.osonmarket.advertising.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import uz.osonmarket.advertising.admin.AdvertisementModel.Status;

@Entity
@Table(name = "advertisements", indexes = @Index(columnList = "status, updated_at"))
class Advertisement {
    @Id UUID id;
    @Column(nullable = false) long revision;
    @Column(nullable = false, length = 160) String titleUz;
    @Column(nullable = false, length = 160) String titleRu;
    @Column(length = 3000) String descriptionUz;
    @Column(length = 3000) String descriptionRu;
    @Column(nullable = false) boolean webEnabled;
    @Column(nullable = false) boolean appEnabled;
    @Column(length = 2048) String targetUrl;
    Instant startsAt;
    Instant endsAt;
    int sortOrder;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) Status status;
    UUID mediaId;
    @Column(nullable = false) UUID createdBy;
    @Column(nullable = false) Instant createdAt;
    @Column(nullable = false) UUID updatedBy;
    @Column(nullable = false) Instant updatedAt;
}
