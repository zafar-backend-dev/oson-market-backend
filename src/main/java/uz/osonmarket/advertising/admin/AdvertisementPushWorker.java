package uz.osonmarket.advertising.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.advertising.admin.AdvertisementModel.Snapshot;
import uz.osonmarket.advertising.admin.AdvertisementModel.Status;
import uz.osonmarket.order.OrderFirebasePush;

@Component
class AdvertisementPushWorker {
    private final AdvertisementDispatchRepository dispatches;
    private final AdvertisementRepository advertisements;
    private final OrderFirebasePush firebase;
    private final ObjectMapper json;

    AdvertisementPushWorker(AdvertisementDispatchRepository dispatches, AdvertisementRepository advertisements,
                            OrderFirebasePush firebase, ObjectMapper json) {
        this.dispatches = dispatches;
        this.advertisements = advertisements;
        this.firebase = firebase;
        this.json = json;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void sendDue() {
        var dispatch = dispatches.lockNextPush(Instant.now()).orElse(null);
        if (dispatch == null) return;
        var ad = advertisements.findById(dispatch.advertisementId).orElse(null);
        if (ad == null || ad.status != Status.PUBLISHED || ad.revision != dispatch.revision
                || (ad.endsAt != null && !ad.endsAt.isAfter(Instant.now()))) {
            dispatch.pushStatus = "CANCELLED";
            dispatch.nextPushAt = null;
            return;
        }
        int status = 503;
        try {
            var snapshot = json.readValue(dispatch.snapshot, Snapshot.class);
            status = firebase.sendPromotion(dispatch.id, snapshot.targetUrl(), snapshot.uz().title(),
                    body(snapshot.uz().description()), snapshot.ru().title(), body(snapshot.ru().description()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return;
        } catch (IOException | JOSEException error) {
            LoggerFactory.getLogger(getClass()).warn("Promotion push {} failed ({})", dispatch.id,
                    error.getClass().getSimpleName());
        }
        dispatch.pushAttempts++;
        if (status == 200) {
            dispatch.pushStatus = "ACCEPTED";
            dispatch.nextPushAt = null;
        } else if (dispatch.pushAttempts < 4 && (status == 429 || status >= 500)) {
            dispatch.nextPushAt = Instant.now().plusSeconds(60L * dispatch.pushAttempts);
        } else {
            dispatch.pushStatus = "FAILED";
            dispatch.nextPushAt = null;
            LoggerFactory.getLogger(getClass()).warn("Promotion push {} failed with HTTP {}", dispatch.id, status);
        }
    }

    private static String body(String text) {
        if (text == null) return "";
        int length = Math.min(200, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, length));
    }
}
