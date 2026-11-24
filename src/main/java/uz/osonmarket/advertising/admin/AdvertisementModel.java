package uz.osonmarket.advertising.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AdvertisementModel {
    private AdvertisementModel() {}

    public enum Status { DRAFT, PUBLISHED, PAUSED, ARCHIVED }
    public enum Channel { WEB, APP }
    public enum Visibility { DRAFT, SCHEDULED, LIVE, ENDED, PAUSED, ARCHIVED }

    public record Translation(@NotBlank @Size(max = 160) String title, @Size(max = 3000) String description) {}

    public record Input(@NotNull @Valid Translation uz, @NotNull @Valid Translation ru,
                        @NotEmpty Set<@NotNull Channel> channels, @Size(max = 2048) String targetUrl,
                        Instant startsAt, Instant endsAt, @NotNull @Min(0) Integer sortOrder) {}

    public record Change(@NotNull @Positive Long expectedRevision, @NotNull Status status,
                         @Size(max = 1000) String reason) {}

    public record Media(UUID id, String filename, String contentType, long sizeBytes, String url) {}

    public record Snapshot(UUID id, long revision, Translation uz, Translation ru, Set<Channel> channels,
                           String targetUrl, Instant startsAt, Instant endsAt, int sortOrder, Status status,
                           Media media, UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt) {}

    public record View(Snapshot advertisement, Visibility visibility) {}
    public record History(long revision, String action, UUID actorId, Instant changedAt, String reason,
                          Snapshot snapshot) {}
}
