package uz.osonmarket.advertising.admin;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.advertising.admin.AdvertisementModel.*;

@RestController
@RequestMapping("/api/admin/advertisements")
@SecurityRequirement(name = "bearerAuth")
public class AdvertisementAdminController {
    private final AdvertisementService advertisements;

    public AdvertisementAdminController(AdvertisementService advertisements) {
        this.advertisements = advertisements;
    }

    @GetMapping
    public PagedModel<View> list(@RequestParam(required = false) Status status,
                                 @RequestParam(required = false) Channel channel,
                                 @RequestParam(defaultValue = "") @Size(max = 160) String q,
                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return advertisements.list(status, channel, q, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public View get(@PathVariable UUID id) {
        return advertisements.get(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@RequestPart("advertisement") @Valid Input input,
                       @RequestPart(value = "file", required = false) MultipartFile file,
                       @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return advertisements.create(input, file, UUID.fromString(jwt.getSubject()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public View update(@PathVariable UUID id, @RequestParam @Positive long expectedRevision,
                       @RequestPart("advertisement") @Valid Input input,
                       @RequestPart(value = "file", required = false) MultipartFile file,
                       @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return advertisements.update(id, expectedRevision, input, file, UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/{id}/status")
    public View changeStatus(@PathVariable UUID id, @Valid @RequestBody Change input,
                             @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return advertisements.change(id, input, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{id}/send")
    public AdvertisementDeliveryService.DispatchView send(@PathVariable UUID id, @Valid @RequestBody Send input,
                          @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return advertisements.send(id, input.expectedRevision(), UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{id}/dispatches")
    public PagedModel<AdvertisementDeliveryService.DispatchView> dispatches(@PathVariable UUID id,
                            @RequestParam(defaultValue = "0") @Min(0) int page,
                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return advertisements.dispatchHistory(id, PageRequest.of(page, size));
    }

    public record Send(@jakarta.validation.constraints.NotNull @Positive Long expectedRevision) {}

    @GetMapping("/{id}/history")
    public PagedModel<History> history(@PathVariable UUID id,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return advertisements.history(id, PageRequest.of(page, size));
    }

    @GetMapping("/{id}/media/{mediaId}")
    public ResponseEntity<? extends Resource> media(@PathVariable UUID id, @PathVariable UUID mediaId) {
        return advertisements.media(id, mediaId);
    }
}
