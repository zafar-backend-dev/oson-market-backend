package uz.osonmarket.advertising.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.advertising.admin.AdvertisementModel.*;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.persistence.AuthLocks;

@Service
class AdvertisementDeliveryService {
    private final AdvertisementDispatchRepository dispatches;
    private final AdvertisementAssets assets;
    private final EntityManager entities;
    private final ObjectMapper json;
    private final AuthLocks locks;

    AdvertisementDeliveryService(AdvertisementDispatchRepository dispatches, AdvertisementAssets assets,
                                 EntityManager entities, ObjectMapper json, AuthLocks locks) {
        this.dispatches = dispatches;
        this.assets = assets;
        this.entities = entities;
        this.json = json;
        this.locks = locks;
    }

    @Transactional(readOnly = true)
    public PagedModel<Delivery> live(Channel channel, Pageable page) {
        return new PagedModel<>(dispatches.live(channel == Channel.WEB, Instant.now(), page)
                .map(dispatch -> delivery(dispatch, channel, null, false)));
    }

    @Transactional(readOnly = true)
    public PagedModel<Delivery> inbox(Channel channel, UUID userId, Pageable page) {
        requireApp(channel);
        return new PagedModel<>(dispatches.inbox(channel == Channel.WEB, Instant.now(), page).map(dispatch -> {
            var read = entities.find(AdvertisementNotificationRead.class,
                    new AdvertisementNotificationRead.Key(userId, dispatch.id));
            return delivery(dispatch, channel, read == null ? null : read.readAt, true);
        }));
    }

    @Transactional(readOnly = true)
    public long unread(Channel channel, UUID userId) {
        requireApp(channel);
        return dispatches.unread(channel == Channel.WEB, Instant.now(), userId);
    }

    @Transactional
    public void read(Channel channel, UUID userId, UUID id) {
        requireApp(channel);
        inboxDispatch(channel, id);
        locks.lock("advertisement-read:" + userId + ":" + id);
        var key = new AdvertisementNotificationRead.Key(userId, id);
        if (entities.find(AdvertisementNotificationRead.class, key) == null) {
            var state = new AdvertisementNotificationRead();
            state.userId = userId;
            state.dispatchId = id;
            state.readAt = Instant.now();
            entities.persist(state);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<? extends Resource> media(Channel channel, UUID id, boolean inbox) {
        requireApp(channel);
        var dispatch = inbox ? inboxDispatch(channel, id) :
                dispatches.liveById(channel == Channel.WEB, Instant.now(), id)
                        .orElseThrow(AdvertisementDeliveryService::notFound);
        return assets.read(dispatch.advertisementId, dispatch.mediaId);
    }

    private AdvertisementDispatch inboxDispatch(Channel channel, UUID id) {
        return dispatches.findById(id)
                .filter(dispatch -> !dispatch.availableAt.isAfter(Instant.now()))
                .filter(dispatch -> channel == Channel.WEB ? dispatch.webEnabled : dispatch.appEnabled)
                .orElseThrow(AdvertisementDeliveryService::notFound);
    }

    private Delivery delivery(AdvertisementDispatch dispatch, Channel channel, Instant readAt, boolean inbox) {
        try {
            var snapshot = json.readValue(dispatch.snapshot, Snapshot.class);
            String prefix = inbox ? "/api/advertisement-notifications/" : "/api/site/advertisements/";
            var media = snapshot.media();
            return new Delivery(dispatch.id, snapshot.id(), snapshot.uz(), snapshot.ru(), snapshot.targetUrl(),
                    media == null ? null : media.contentType(), media == null ? null : prefix + dispatch.id + "/media?channel=" + channel,
                    dispatch.availableAt, readAt);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid stored advertisement dispatch", ex);
        }
    }

    static DispatchView dispatchView(AdvertisementDispatch dispatch) {
        var channels = EnumSet.noneOf(Channel.class);
        if (dispatch.webEnabled) channels.add(Channel.WEB);
        if (dispatch.appEnabled) channels.add(Channel.APP);
        return new DispatchView(dispatch.id, dispatch.advertisementId, dispatch.revision, channels,
                dispatch.actorId, dispatch.sentAt, dispatch.availableAt, dispatch.pushStatus);
    }

    private static void requireApp(Channel channel) {
        if (channel != Channel.APP) throw notFound();
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "advertisement.notFound");
    }

    public record DispatchView(UUID id, UUID advertisementId, long revision, Set<Channel> channels,
                               UUID actorId, Instant sentAt, Instant availableAt, String pushStatus) {}

    public record Delivery(UUID id, UUID advertisementId, Translation uz, Translation ru, String targetUrl,
                           String contentType, String mediaUrl, Instant createdAt, Instant readAt) {}
}
