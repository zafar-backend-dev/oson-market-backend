package uz.osonmarket.branch;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.profile.AvatarImages;

@RestController
@RequestMapping("/api/admin/branches")
@SecurityRequirement(name = "bearerAuth")
public class BranchAdminController {
    private final BranchService branches;
    private final AvatarImages images;

    public BranchAdminController(BranchService branches, AvatarImages images) {
        this.branches = branches;
        this.images = images;
    }

    @GetMapping
    public PagedModel<BranchService.View> list(Pageable pageable) {
        return branches.list(pageable);
    }

    @GetMapping("/{id}")
    public BranchService.View get(@PathVariable UUID id) {
        return branches.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BranchService.View create(@Valid @RequestBody Input input) {
        return branches.save(null, input, null);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BranchService.View update(@PathVariable UUID id, @Valid @RequestBody Input input) {
        return branches.save(id, input, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BranchService.View createWithImage(@Valid @RequestPart("branch") Input input,
                                              @RequestPart("image") MultipartFile image) {
        return branches.save(null, input, images.read(image));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BranchService.View replaceImage(@PathVariable UUID id, @RequestPart("image") MultipartFile image) {
        return branches.image(id, images.read(image));
    }

    @GetMapping(value = "/{id}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .body(branches.image(id));
    }

    @DeleteMapping("/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeImage(@PathVariable UUID id) {
        branches.image(id, null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        branches.archive(id);
    }

    @PostMapping("/{id}/delivery-quote")
    public BranchService.Quote quote(@PathVariable UUID id, @Valid @RequestBody Location location) {
        return branches.quote(id, location);
    }

    public record Location(@NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
                           @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude) {}

    public record Input(@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String code,
                        @NotBlank @Size(max = 255) String nameUz, @NotBlank @Size(max = 255) String nameRu,
                        @NotBlank @Size(max = 255) String addressLine, @NotBlank @Size(max = 120) String city,
                        @NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{7,32}") String phone,
                        @Email @Size(max = 255) String email, @Size(max = 255) String workingHours,
                        @NotNull @Valid Location location, @NotNull Boolean active, @NotNull Boolean pickupEnabled,
                        @NotNull Boolean deliveryEnabled,
                        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal pricePerKm) {}
}
