package uz.osonmarket.branding;

import jakarta.persistence.*;
import java.io.IOException;
import java.time.Instant;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.catalog.media.CatalogImages;
import uz.osonmarket.domain.media.MediaFile;
import uz.osonmarket.profile.AvatarImages;
import uz.osonmarket.profile.AvatarImages.Image;

@RestController
public class SiteLogoController {
    private final SiteLogoService logos;
    private final AvatarImages images;

    public SiteLogoController(SiteLogoService logos, AvatarImages images) {
        this.logos = logos;
        this.images = images;
    }

    @GetMapping(value = "/api/site/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> logo() {
        var image = logos.read();
        if (image == null) return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(image);
    }

    @PutMapping(value = "/api/admin/site/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LogoView update(@RequestPart("file") MultipartFile file) {
        return logos.replace(images.readAvatar(file));
    }

    @Bean
    ApplicationRunner seedSiteLogo() {
        return args -> logos.seed();
    }

    public record LogoView(String url, Instant updatedAt) {}
}

@Service
class SiteLogoService {
    private static final String NAMESPACE = "site-logo";
    private final EntityManager em;
    private final CatalogImages images;
    private final AuthLocks locks;

    SiteLogoService(EntityManager em, CatalogImages images, AuthLocks locks) {
        this.em = em;
        this.images = images;
        this.locks = locks;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] read() {
        var logo = em.find(SiteLogo.class, 1);
        return logo == null ? null : images.readMedia(logo.media);
    }

    @Transactional
    public void seed() throws IOException {
        locks.lock(NAMESPACE);
        if (em.find(SiteLogo.class, 1) != null) return;
        try (var input = new ClassPathResource("branding/llogo.png").getInputStream()) {
            byte[] bytes = input.readAllBytes();
            try (var metadata = new java.io.ByteArrayInputStream(bytes)) {
                var image = javax.imageio.ImageIO.read(metadata);
                if (image == null) throw new IOException("Invalid bundled site logo");
                store(null, new Image(bytes, image.getWidth(), image.getHeight()));
            }
        }
    }

    @Transactional
    public SiteLogoController.LogoView replace(Image image) {
        locks.lock(NAMESPACE);
        return store(em.find(SiteLogo.class, 1), image);
    }

    private SiteLogoController.LogoView store(SiteLogo current, Image image) {
        var logo = current == null ? new SiteLogo() : current;
        var previous = logo.media;
        logo.media = images.store(image, NAMESPACE);
        logo.updatedAt = Instant.now();
        if (current == null) em.persist(logo);
        em.flush();
        images.deleteOwned(previous, NAMESPACE);
        return new SiteLogoController.LogoView("/api/site/logo", logo.updatedAt);
    }
}

@Entity
@Table(name = "site_logo")
class SiteLogo {
    @Id
    Integer id = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    MediaFile media;

    @Column(nullable = false)
    Instant updatedAt;
}
