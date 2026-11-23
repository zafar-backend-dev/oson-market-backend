package uz.osonmarket.order;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.branch.BranchAdminController.Location;

@RestController
@RequestMapping("/api/admin/orders")
@SecurityRequirement(name = "bearerAuth")
public class AdminOrderController {
    private final OrderOperationsService operations;

    public AdminOrderController(OrderOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping
    public PagedModel<OrderOperationsService.AdminRow> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String orderNumber, Pageable pageable) {
        return operations.list(status, orderNumber, pageable);
    }

    @GetMapping("/{id}")
    public OrderOperationsService.AdminDetail get(@PathVariable UUID id, Locale locale) {
        return operations.get(id, locale);
    }

    @PatchMapping("/{id}/status")
    public OrderOperationsService.AdminDetail change(@PathVariable UUID id, @Valid @RequestBody StatusRequest request,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, Locale locale) {
        return operations.change(id, request, UUID.fromString(jwt.getSubject()), locale);
    }

    @PutMapping("/{id}/fulfillment")
    public OrderOperationsService.AdminDetail fulfillment(@PathVariable UUID id, @Valid @RequestBody FulfillmentRequest request,
                                                          Locale locale) {
        return operations.fulfillment(id, request, locale);
    }

    public record StatusRequest(@NotNull OrderStatus expectedStatus, @NotNull OrderStatus status,
                                @Size(max = 1000) String reason) {}

    public record FulfillmentRequest(@NotNull OrderStatus expectedStatus, @NotNull FulfillmentMethod method,
                                     @NotNull UUID branchId, @Size(max = 255) String deliveryAddress,
                                     @Valid Location location) {}
}
