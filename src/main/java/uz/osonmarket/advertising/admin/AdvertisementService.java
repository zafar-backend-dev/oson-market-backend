package uz.osonmarket.advertising.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.advertising.admin.AdvertisementModel.*;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.persistence.AuthLocks;

@Service
class AdvertisementService {
    private final EntityManager entities;
    private final AdvertisementRepository advertisements;
    private final AdvertisementHistoryRepository history;
    private final AdvertisementAssets assets;
    private final AdvertisementDispatchRepository dispatches;
    private final AuthLocks locks;
    private final ObjectMapper json;

    AdvertisementService(EntityManager entities, AdvertisementRepository advertisements,
                         AdvertisementHistoryRepository history, AdvertisementAssets assets, AuthLocks locks,
                         ObjectMapper json, AdvertisementDispatchRepository dispatches) {
        this.entities = entities;
        this.advertisements = advertisements;
        this.history = history;
        this.assets = assets;
        this.locks = locks;
        this.json = json;
        this.dispatches = dispatches;
    }

    @Transactional(readOnly = true)
    public PagedModel<View> list(Status status, Channel channel, String q, Pageable pageable) {
        Instant now = Instant.now();
        String pattern = "%" + q.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new PagedModel<>(advertisements.search(status, channel == Channel.WEB, channel == Channel.APP,
                pattern, pageable).map(ad -> view(snapshot(ad), now)));
    }

    @Transactional(readOnly = true)
    public View get(UUID id) {
        return view(snapshot(find(id)), Instant.now());
    }

    @Transactional(readOnly = true)
    public PagedModel<History> history(UUID id, Pageable pageable) {
        find(id);
        return new PagedModel<>(history.findByAdvertisementIdOrderByRevisionDesc(id, pageable).map(event -> {
            try {
                return new History(event.revision, event.action, event.actorId, event.changedAt, event.reason,
                        json.readValue(event.snapshot, Snapshot.class));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Invalid stored advertisement revision", ex);
            }
        }));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<? extends Resource> media(UUID id, UUID mediaId) {
        return assets.read(id, mediaId);
    }

    @Transactional
    public View create(Input input, MultipartFile file, UUID actor) {
        Instant now = Instant.now();
        var ad = new Advertisement();
        ad.id = UUID.randomUUID();
        ad.status = Status.DRAFT;
        ad.createdAt = now;
        ad.createdBy = actor;
        apply(ad, input, file, now);
        View result = record(ad, "CREATED", actor, now, null);
        entities.persist(ad);
        return result;
    }

    @Transactional
    public View update(UUID id, long revision, Input input, MultipartFile file, UUID actor) {
        Advertisement ad = editable(id, revision);
        Instant now = Instant.now();
        apply(ad, input, file, now);
        validatePublication(ad, now);
        return record(ad, "UPDATED", actor, now, null);
    }

    @Transactional
    public View change(UUID id, Change change, UUID actor) {
        Advertisement ad = editable(id, change.expectedRevision());
        Instant now = Instant.now();
        if (ad.status == change.status()) {
            return view(snapshot(ad), now);
        }
        boolean allowed = switch (ad.status) {
            case DRAFT -> change.status() == Status.PUBLISHED || change.status() == Status.ARCHIVED;
            case PUBLISHED -> change.status() == Status.PAUSED || change.status() == Status.ARCHIVED;
            case PAUSED -> change.status() == Status.PUBLISHED || change.status() == Status.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw new AuthException(HttpStatus.CONFLICT, "advertisement.invalidStatus");
        }
        ad.status = change.status();
        validatePublication(ad, now);
        return record(ad, ad.status.name(), actor, now, change.reason());
    }

    @Transactional
    public AdvertisementDeliveryService.DispatchView send(UUID id, long revision, UUID actor) {
        locks.lock("advertisement:" + id);
        var previous = dispatches.findByAdvertisementIdAndRequestedRevision(id, revision)
                .or(() -> dispatches.revision(id, revision));
        if (previous.isPresent()) return AdvertisementDeliveryService.dispatchView(previous.get());

        Advertisement ad = editable(id, revision);
        Instant now = Instant.now();
        ad.status = Status.PUBLISHED;
        validatePublication(ad, now);
        Snapshot snapshot = record(ad, "SENT", actor, now, null).advertisement();
        var sent = new AdvertisementDispatch();
        sent.id = UUID.randomUUID();
        sent.advertisementId = id;
        sent.requestedRevision = revision;
        sent.revision = ad.revision;
        sent.webEnabled = ad.webEnabled;
        sent.appEnabled = ad.appEnabled;
        sent.actorId = actor;
        sent.sentAt = now;
        sent.availableAt = ad.startsAt != null && ad.startsAt.isAfter(now) ? ad.startsAt : now;
        sent.mediaId = ad.mediaId;
        try {
            sent.snapshot = json.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize advertisement dispatch", ex);
        }
        entities.persist(sent);
        return AdvertisementDeliveryService.dispatchView(sent);
    }

    @Transactional(readOnly = true)
    public PagedModel<AdvertisementDeliveryService.DispatchView> dispatchHistory(UUID id, Pageable pageable) {
        find(id);
        return new PagedModel<>(dispatches.findByAdvertisementIdOrderBySentAtDescIdDesc(id, pageable)
                .map(AdvertisementDeliveryService::dispatchView));
    }

    private Advertisement editable(UUID id, long revision) {
        locks.lock("advertisement:" + id);
        Advertisement ad = find(id);
        if (ad.revision != revision) {
            throw new AuthException(HttpStatus.CONFLICT, "advertisement.stale");
        }
        if (ad.status == Status.ARCHIVED) {
            throw new AuthException(HttpStatus.CONFLICT, "advertisement.invalidStatus");
        }
        return ad;
    }

    private Advertisement find(UUID id) {
        return advertisements.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "advertisement.notFound"));
    }

    private void apply(Advertisement ad, Input input, MultipartFile file, Instant now) {
        if (input.endsAt() != null && input.startsAt() != null && !input.endsAt().isAfter(input.startsAt())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "advertisement.invalidSchedule");
        }
        ad.titleUz = input.uz().title().strip();
        ad.titleRu = input.ru().title().strip();
        ad.descriptionUz = input.uz().description();
        ad.descriptionRu = input.ru().description();
        ad.webEnabled = input.channels().contains(Channel.WEB);
        ad.appEnabled = input.channels().contains(Channel.APP);
        ad.targetUrl = target(input.targetUrl());
        ad.startsAt = input.startsAt();
        ad.endsAt = input.endsAt();
        ad.sortOrder = input.sortOrder();
        if (file != null) {
            ad.mediaId = assets.store(ad.id, file, now);
        }
    }

    private String target(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI url = URI.create(value.strip());
            if (!url.isAbsolute() && url.getRawAuthority() == null && url.toString().startsWith("/")
                    && !url.toString().startsWith("//")) {
                return url.toString();
            }
            if ("https".equalsIgnoreCase(url.getScheme()) && url.getHost() != null && url.getUserInfo() == null) {
                return url.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid URLs share one localized error.
        }
        throw new AuthException(HttpStatus.BAD_REQUEST, "advertisement.invalidTarget");
    }

    private void validatePublication(Advertisement ad, Instant now) {
        if (ad.status == Status.PUBLISHED) {
            if (ad.mediaId == null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "advertisement.mediaRequired");
            }
            if (ad.endsAt != null && !ad.endsAt.isAfter(now)) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "advertisement.invalidSchedule");
            }
        }
    }

    private View record(Advertisement ad, String action, UUID actor, Instant now, String reason) {
        ad.revision++;
        ad.updatedBy = actor;
        ad.updatedAt = now;
        Snapshot snapshot = snapshot(ad);
        var event = new AdvertisementHistory();
        event.advertisementId = ad.id;
        event.revision = ad.revision;
        event.action = action;
        event.actorId = actor;
        event.changedAt = now;
        event.reason = reason;
        try {
            event.snapshot = json.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize advertisement revision", ex);
        }
        entities.persist(event);
        return view(snapshot, now);
    }

    private Snapshot snapshot(Advertisement ad) {
        var channels = EnumSet.noneOf(Channel.class);
        if (ad.webEnabled) channels.add(Channel.WEB);
        if (ad.appEnabled) channels.add(Channel.APP);
        return new Snapshot(ad.id, ad.revision, new Translation(ad.titleUz, ad.descriptionUz),
                new Translation(ad.titleRu, ad.descriptionRu), channels, ad.targetUrl, ad.startsAt,
                ad.endsAt, ad.sortOrder, ad.status, assets.metadata(ad.id, ad.mediaId), ad.createdBy,
                ad.createdAt, ad.updatedBy, ad.updatedAt);
    }

    private View view(Snapshot ad, Instant now) {
        Visibility visibility = switch (ad.status()) {
            case DRAFT -> Visibility.DRAFT;
            case PAUSED -> Visibility.PAUSED;
            case ARCHIVED -> Visibility.ARCHIVED;
            case PUBLISHED -> {
                if (ad.endsAt() != null && !now.isBefore(ad.endsAt())) yield Visibility.ENDED;
                if (ad.startsAt() != null && now.isBefore(ad.startsAt())) yield Visibility.SCHEDULED;
                yield Visibility.LIVE;
            }
        };
        return new View(ad, visibility);
    }
}
