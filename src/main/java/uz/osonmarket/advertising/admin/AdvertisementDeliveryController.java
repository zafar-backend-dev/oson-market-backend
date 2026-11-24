package uz.osonmarket.advertising.admin;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.advertising.admin.AdvertisementModel.Channel;
import uz.osonmarket.advertising.admin.AdvertisementDeliveryService.Delivery;

@RestController
public class AdvertisementDeliveryController {
    private final AdvertisementDeliveryService deliveries;

    public AdvertisementDeliveryController(AdvertisementDeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @GetMapping("/api/advertisement-notifications")
    public PagedModel<Delivery> inbox(@RequestParam(defaultValue = "WEB") Channel channel, Pageable page,
                                     @AuthenticationPrincipal Jwt jwt) {
        return deliveries.inbox(channel, UUID.fromString(jwt.getSubject()), page);
    }

    @GetMapping("/api/advertisement-notifications/unread-count")
    public UnreadCount unread(@RequestParam(defaultValue = "WEB") Channel channel, @AuthenticationPrincipal Jwt jwt) {
        return new UnreadCount(deliveries.unread(channel, UUID.fromString(jwt.getSubject())));
    }

    @PatchMapping("/api/advertisement-notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable UUID id, @RequestParam(defaultValue = "WEB") Channel channel,
                     @AuthenticationPrincipal Jwt jwt) {
        deliveries.read(channel, UUID.fromString(jwt.getSubject()), id);
    }

    @GetMapping("/api/advertisement-notifications/{id}/media")
    public ResponseEntity<? extends Resource> inboxMedia(@PathVariable UUID id,
                                         @RequestParam(defaultValue = "WEB") Channel channel) {
        return deliveries.media(channel, id, true);
    }

    public record UnreadCount(long count) {}
}
