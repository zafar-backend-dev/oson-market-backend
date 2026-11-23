package uz.osonmarket.branch;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import uz.osonmarket.domain.media.MediaFile;

@Entity
@Table(name = "branches")
class Branch {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(nullable = false, unique = true, length = 64)
    String code;
    String nameUz;
    String nameRu;
    String addressLine;
    String city;
    @Column(length = 32)
    String phone;
    String email;
    String workingHours;
    @Column(precision = 10, scale = 7)
    BigDecimal latitude;
    @Column(precision = 10, scale = 7)
    BigDecimal longitude;
    String status;
    Boolean pickupEnabled;
    Boolean deliveryEnabled;
    @Column(precision = 14, scale = 2)
    BigDecimal pricePerKm;
    @ManyToOne(fetch = FetchType.LAZY)
    MediaFile image;
    Instant createdAt;
    Instant updatedAt;
}
