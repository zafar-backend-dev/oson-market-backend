package uz.osonmarket.advertising.admin;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "advertisement_media_content")
class AdvertisementMediaContent {
    @Id UUID id;
    @Column(nullable = false, columnDefinition = "bytea") byte[] content;
}
