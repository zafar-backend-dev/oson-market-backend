package uz.osonmarket.advertising.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advertisement_history", uniqueConstraints = @UniqueConstraint(columnNames = {"advertisement_id", "revision"}))
class AdvertisementHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) UUID id;
    @Column(nullable = false) UUID advertisementId;
    @Column(nullable = false) long revision;
    @Column(nullable = false, length = 32) String action;
    @Column(nullable = false) UUID actorId;
    @Column(nullable = false) Instant changedAt;
    @Column(length = 1000) String reason;
    @Column(nullable = false, columnDefinition = "text") String snapshot;
}
