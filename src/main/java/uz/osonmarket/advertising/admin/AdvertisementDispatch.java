package uz.osonmarket.advertising.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advertisement_dispatches", indexes = @Index(columnList = "available_at"),
       uniqueConstraints = @UniqueConstraint(columnNames = {"advertisement_id", "revision"}))
class AdvertisementDispatch {
    @Id UUID id;
    @Column(nullable = false) UUID advertisementId;
    @Column(nullable = false) long revision;
    @Column(nullable = false) long requestedRevision;
    @Column(nullable = false) boolean webEnabled;
    @Column(nullable = false) boolean appEnabled;
    @Column(nullable = false) UUID actorId;
    @Column(nullable = false) Instant sentAt;
    @Column(nullable = false) Instant availableAt;
    UUID mediaId;
    @Column(nullable = false, columnDefinition = "text") String snapshot;
}
