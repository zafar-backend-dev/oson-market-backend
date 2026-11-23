package uz.osonmarket.catalog.admin.brand;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.profile.AvatarImages;

@RestController
@RequestMapping("/api/admin/brands")
@SecurityRequirement(name = "bearerAuth")
public class BrandAdminController {
    private final BrandAdminService brands;
    private final AvatarImages images;

    public BrandAdminController(BrandAdminService brands, AvatarImages images) {
        this.brands = brands;
        this.images = images;
    }

    @GetMapping
    public PagedModel<BrandResponse> list(Pageable pageable, @RequestParam(required = false) String q,
                                          @RequestParam(required = false) Boolean active) {
        return brands.listBrand(pageable, q, active);
    }

    @GetMapping("/{id}")
    public BrandResponse get(@PathVariable UUID id) {
        return brands.getBrand(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BrandResponse create(@Valid @RequestBody BrandInput input) {
        return brands.saveBrand(null, input);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BrandResponse update(@PathVariable UUID id, @Valid @RequestBody BrandInput input) {
        return brands.saveBrand(id, input);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BrandResponse createWithLogo(@Valid @RequestPart("brand") BrandInput input,
                                        @RequestPart(value = "file", required = false) MultipartFile file) {
        return brands.saveBrandWithLogo(null, input, file == null ? null : images.read(file), false);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BrandResponse updateWithLogo(@PathVariable UUID id, @Valid @RequestPart("brand") BrandInput input,
                                        @RequestPart(value = "file", required = false) MultipartFile file,
                                        @RequestParam(defaultValue = "false") boolean removeLogo) {
        return brands.saveBrandWithLogo(id, input, file == null ? null : images.read(file), removeLogo);
    }

    @GetMapping(value = "/{id}/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> logo(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(brands.logo(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        brands.deleteBrand(id);
    }
}
