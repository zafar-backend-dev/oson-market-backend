package uz.osonmarket.order;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.branch.BranchAdminController.Location;
import uz.osonmarket.branch.BranchService;

@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderService orders;
    private final BranchService branches;

    public OrderController(OrderService orders, BranchService branches) {
        this.orders = orders;
        this.branches = branches;
    }

    @PostMapping("/delivery-quote/{branchId}")
    public BranchService.Quote quote(@PathVariable UUID branchId, @Valid @RequestBody Location location) {
        return branches.quote(branchId, location);
    }

    @GetMapping("/{id}/history")
    public java.util.List<OrderService.StatusEntry> history(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return orders.history(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping
    public ResponseEntity<OrderView> create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody OrderRequest input, Locale locale) {
        var result = orders.create(UUID.fromString(jwt.getSubject()), input, locale.getLanguage());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    @GetMapping
    public PagedModel<OrderView> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                      Pageable pageable, Locale locale) {
        return orders.list(UUID.fromString(jwt.getSubject()), pageable, locale.getLanguage());
    }

    @GetMapping("/{id}")
    public OrderView get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID id, Locale locale) {
        return orders.get(UUID.fromString(jwt.getSubject()), id, locale.getLanguage());
    }

    @PatchMapping("/{id}/cancel")
    public OrderView cancel(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @Valid @RequestBody CancelRequest request, Locale locale) {
        return orders.cancel(UUID.fromString(jwt.getSubject()), id, request.reason(), locale.getLanguage());
    }

    @PutMapping("/{id}/rating")
    public OrderView rate(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                          @Valid @RequestBody RatingRequest request, Locale locale) {
        return orders.rate(UUID.fromString(jwt.getSubject()), id, request, locale.getLanguage());
    }

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {}
    public record RatingRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {}
}
