package uz.osonmarket.order;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/notifications")
@SecurityRequirement(name = "bearerAuth")
public class OrderNotificationController {
    private final OrderOperationsService operations;

    public OrderNotificationController(OrderOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping
    public PagedModel<OrderOperationsService.Notification> inbox(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, Pageable pageable, Locale locale) {
        return operations.inbox(UUID.fromString(jwt.getSubject()), pageable, locale);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return new UnreadCount(operations.unread(UUID.fromString(jwt.getSubject())));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        operations.read(UUID.fromString(jwt.getSubject()), id);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        operations.readAll(UUID.fromString(jwt.getSubject()));
    }

    public record UnreadCount(long count) {}
}
