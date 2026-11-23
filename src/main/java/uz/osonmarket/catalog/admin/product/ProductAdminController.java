package uz.osonmarket.catalog.admin.product;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.catalog.admin.shared.Entry;

@RestController
@RequestMapping("/api/admin/products")
@SecurityRequirement(name = "bearerAuth")
public class ProductAdminController {
    private final ProductAdminService catalog;
    private final ProductFormService forms;

    public ProductAdminController(ProductAdminService catalog, ProductFormService forms) {
        this.catalog = catalog;
        this.forms = forms;
    }

    @GetMapping
    public PagedModel<Entry<ProductInput>> list(Pageable pageable, @RequestParam(required = false) UUID categoryId) {
        return catalog.listProduct(pageable, categoryId);
    }

    @GetMapping("/{id}")
    public ProductDetail get(@PathVariable UUID id) {
        return forms.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Entry<ProductInput> create(@Valid @RequestBody ProductInput input) {
        return catalog.saveProduct(null, input);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Entry<ProductInput> update(@PathVariable UUID id, @Valid @RequestBody ProductInput input) {
        return catalog.saveProduct(id, input);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetail createWithVariants(@Valid @RequestPart("product") ProductForm form,
                                             @RequestParam MultiValueMap<String, MultipartFile> files) {
        return forms.save(null, form, files);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductDetail updateWithVariants(@PathVariable UUID id, @Valid @RequestPart("product") ProductForm form,
                                             @RequestParam MultiValueMap<String, MultipartFile> files) {
        return forms.save(id, form, files);
    }

    @GetMapping(value = "/{id}/images/{imageId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable UUID id, @PathVariable UUID imageId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(forms.image(id, imageId));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        forms.removeImage(id, imageId);
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        forms.removeVariant(id, variantId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        forms.delete(id);
    }
}
