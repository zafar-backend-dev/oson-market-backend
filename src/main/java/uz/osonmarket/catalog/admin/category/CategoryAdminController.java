package uz.osonmarket.catalog.admin.category;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.profile.AvatarImages;

@RestController
@RequestMapping("/api/admin/categories")
@SecurityRequirement(name = "bearerAuth")
public class CategoryAdminController {
    private final CategoryAdminService catalog;
    private final AvatarImages images;

    public CategoryAdminController(CategoryAdminService catalog, AvatarImages images) {
        this.catalog = catalog;
        this.images = images;
    }

    @GetMapping
    public PagedModel<CategoryResponse> list(Pageable pageable) {
        return catalog.listCategory(pageable);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return catalog.getCategory(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryInput input) {
        return catalog.saveCategory(null, input);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryInput input) {
        return catalog.saveCategory(id, input);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createWithImage(@Valid @RequestPart("category") CategoryInput input,
                                          @RequestPart(value = "file", required = false) MultipartFile file) {
        return catalog.saveCategory(null, input, file == null ? null : images.read(file), false, true);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CategoryResponse updateWithImage(@PathVariable UUID id,
                                            @Valid @RequestPart("category") CategoryInput input,
                                            @RequestPart(value = "file", required = false) MultipartFile file,
                                            @RequestParam(defaultValue = "false") boolean removeImage) {
        return catalog.saveCategory(id, input, file == null ? null : images.read(file), removeImage, true);
    }

    @GetMapping(value = "/{id}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] image(@PathVariable UUID id) {
        return catalog.image(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        catalog.deleteCategory(id);
    }
}
