package uz.osonmarket.advertising.admin;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.profile.AvatarImages;

@Component
class AdvertisementAssets {
    private final EntityManager entities;
    private final AvatarImages images;
    private final long videoLimit;

    AdvertisementAssets(EntityManager entities, AvatarImages images,
                        @Value("${app.advertising.max-video-bytes:26214400}") long videoLimit) {
        this.entities = entities;
        this.images = images;
        this.videoLimit = videoLimit;
    }

    UUID store(UUID advertisementId, MultipartFile file, Instant now) {
        String name = file.getOriginalFilename();
        if (name == null) {
            throw invalid();
        }
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank() || name.length() > 255) {
            throw invalid();
        }

        boolean video = "video/mp4".equalsIgnoreCase(file.getContentType())
                || name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4");
        byte[] bytes = video ? mp4(file) : images.read(file).content();
        var media = new AdvertisementMedia();
        media.id = UUID.randomUUID();
        media.advertisementId = advertisementId;
        media.filename = name;
        media.contentType = video ? "video/mp4" : MediaType.IMAGE_PNG_VALUE;
        media.sizeBytes = bytes.length;
        media.createdAt = now;
        entities.persist(media);

        var content = new AdvertisementMediaContent();
        content.id = media.id;
        content.content = bytes;
        entities.persist(content);
        return media.id;
    }

    AdvertisementModel.Media metadata(UUID advertisementId, UUID mediaId) {
        if (mediaId == null) {
            return null;
        }
        var media = find(advertisementId, mediaId);
        return new AdvertisementModel.Media(media.id, media.filename, media.contentType, media.sizeBytes,
                "/api/admin/advertisements/" + advertisementId + "/media/" + media.id);
    }

    ResponseEntity<ByteArrayResource> read(UUID advertisementId, UUID mediaId) {
        var media = find(advertisementId, mediaId);
        var content = entities.find(AdvertisementMediaContent.class, mediaId);
        if (content == null) {
            throw notFound();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.contentType))
                .cacheControl(CacheControl.noStore()).body(new ByteArrayResource(content.content));
    }

    private AdvertisementMedia find(UUID advertisementId, UUID mediaId) {
        var media = entities.find(AdvertisementMedia.class, mediaId);
        if (media == null || !advertisementId.equals(media.advertisementId)) {
            throw notFound();
        }
        return media;
    }

    private byte[] mp4(MultipartFile file) {
        if (file.getSize() > videoLimit) {
            throw new AuthException(HttpStatus.PAYLOAD_TOO_LARGE, "advertisement.videoSize");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!hasMp4Container(bytes)) {
                throw invalid();
            }
            return bytes;
        } catch (IOException ex) {
            throw invalid();
        }
    }

    private boolean hasMp4Container(byte[] bytes) {
        ByteBuffer boxes = ByteBuffer.wrap(bytes);
        boolean movie = false;
        boolean media = false;
        for (int offset = 0; offset < bytes.length;) {
            if (bytes.length - offset < 8) return false;
            long size = Integer.toUnsignedLong(boxes.getInt(offset));
            int type = boxes.getInt(offset + 4);
            int header = 8;
            if (size == 1) {
                if (bytes.length - offset < 16) return false;
                size = boxes.getLong(offset + 8);
                header = 16;
            } else if (size == 0) {
                size = bytes.length - offset;
            }
            if (size < header || size > bytes.length - offset) return false;
            if (offset == 0 && (type != 0x66747970 || size < header + 8)) return false; // ftyp
            if (type == 0x6d6f6f76 && size > header) movie = true; // moov
            if (type == 0x6d646174 && size > header) media = true; // mdat
            offset += (int) size;
        }
        return movie && media;
    }

    private AuthException invalid() {
        return new AuthException(HttpStatus.BAD_REQUEST, "advertisement.invalidMedia");
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "advertisement.mediaNotFound");
    }
}
