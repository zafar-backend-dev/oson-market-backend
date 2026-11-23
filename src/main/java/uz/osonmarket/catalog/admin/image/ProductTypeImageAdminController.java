package uz.osonmarket.catalog.admin.image;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.catalog.admin.shared.Entry;

@RestController
@RequestMapping("/api/admin/product-type-images")
@SecurityRequirement(name = "bearerAuth")
public class ProductTypeImageAdminController {
    private final ProductTypeImageAdminService catalog;

    public ProductTypeImageAdminController(ProductTypeImageAdminService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public PagedModel<Entry<ImageInput>> list(Pageable pageable) {
        return catalog.listProductTypeImage(pageable);
    }

    @GetMapping("/{id}")
    public Entry<ImageInput> get(@PathVariable UUID id) {
        return catalog.getProductTypeImage(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Entry<ImageInput> create(@Valid @RequestBody ImageInput input) {
        return catalog.saveProductTypeImage(null, input);
    }

    @PutMapping("/{id}")
    public Entry<ImageInput> update(@PathVariable UUID id, @Valid @RequestBody ImageInput input) {
        return catalog.saveProductTypeImage(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        catalog.deleteProductTypeImage(id);
    }
}
