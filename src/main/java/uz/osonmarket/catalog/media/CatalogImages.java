package uz.osonmarket.catalog.media;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.domain.catalog.Category;
import uz.osonmarket.domain.media.MediaFile;
import uz.osonmarket.profile.AvatarImages.Image;

@Component
public class CatalogImages {
    private final EntityManager em;

    public CatalogImages(EntityManager em) {
        this.em = em;
    }

    public MediaFile store(Image image) {
        return store(image, "category");
    }

    public MediaFile store(Image image, String namespace) {
        MediaFile media = new MediaFile();
        media.setStorageKey(namespace + "/" + UUID.randomUUID());
        media.setMimeType("image/png");
        media.setSizeBytes((long) image.content().length);
        media.setWidth(image.width());
        media.setHeight(image.height());
        media.setCreatedAt(Instant.now());
        em.persist(media);
        em.persist(new CategoryImageContent(media.getId(), image.content()));
        return media;
    }

    public String url(Category category, boolean admin) {
        return category.getImageMedia() == null ? null
                : (admin ? "/api/admin/categories/" : "/api/categories/") + category.getId() + "/image";
    }

    public byte[] read(Category category) {
        return readMedia(category.getImageMedia());
    }

    public boolean isStored(MediaFile media) {
        return media != null && media.getStorageKey() != null
                && (media.getStorageKey().startsWith("category/") || media.getStorageKey().startsWith("product/")
                    || media.getStorageKey().startsWith("brand/"));
    }

    public byte[] readMedia(MediaFile media) {
        var image = media == null ? null : em.find(CategoryImageContent.class, media.getId());
        if (image == null) {
            throw new AuthException(HttpStatus.NOT_FOUND, "catalog.notFound");
        }
        return image.content();
    }

    public void deleteOwned(MediaFile media, String namespace) {
        if (media == null || !media.getStorageKey().startsWith(namespace + "/")) return;
        var content = em.find(CategoryImageContent.class, media.getId());
        if (content != null) em.remove(content);
        em.remove(media);
    }
}
