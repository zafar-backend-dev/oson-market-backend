package uz.osonmarket.about;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import uz.osonmarket.about.AboutAdminController.Platform;

@Entity
@Table(name = "site_about")
class AboutPage {
    static final int ID = 1;

    @Id
    Integer id = ID;

    @Column(nullable = false, length = 200)
    String titleUz;

    @Column(nullable = false, length = 10000)
    String descriptionUz;

    @Column(nullable = false, length = 200)
    String titleRu;

    @Column(nullable = false, length = 10000)
    String descriptionRu;

    @Column(nullable = false)
    Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "site_about_social_links", joinColumns = @JoinColumn(name = "about_id"))
    @MapKeyColumn(name = "platform", length = 32)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "url", nullable = false, length = 2048)
    Map<Platform, String> socialLinks = new EnumMap<>(Platform.class);

    protected AboutPage() {}
}
