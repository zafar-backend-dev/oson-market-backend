package uz.osonmarket.catalog.admin.variant;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.catalog.admin.shared.Entry;

@RestController
@RequestMapping("/api/admin/product-types")
@SecurityRequirement(name = "bearerAuth")
public class ProductTypeAdminController {
    private final ProductTypeAdminService catalog;

    public ProductTypeAdminController(ProductTypeAdminService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public PagedModel<Entry<TypeInput>> list(Pageable pageable) {
        return catalog.listProductType(pageable);
    }

    @GetMapping("/{id}")
    public Entry<TypeInput> get(@PathVariable UUID id) {
        return catalog.getProductType(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Entry<TypeInput> create(@Valid @RequestBody TypeInput input) {
        return catalog.saveProductType(null, input);
    }

    @PutMapping("/{id}")
    public Entry<TypeInput> update(@PathVariable UUID id, @Valid @RequestBody TypeInput input) {
        return catalog.saveProductType(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        catalog.deleteProductType(id);
    }
}
