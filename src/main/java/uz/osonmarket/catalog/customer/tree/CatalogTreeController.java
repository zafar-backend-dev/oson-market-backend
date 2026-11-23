package uz.osonmarket.catalog.customer.tree;

import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogTreeController {
    private final CatalogTreeService catalog;

    public CatalogTreeController(CatalogTreeService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/tree")
    public ResponseEntity<CatalogTree> tree(Locale locale) {
        return ResponseEntity.ok().varyBy(HttpHeaders.ACCEPT_LANGUAGE).body(catalog.tree(locale.getLanguage()));
    }

    @GetMapping(value = "/products/{id}/images/{imageId}", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] productImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        return catalog.productImage(id, imageId);
    }

    @GetMapping(value = "/brands/{id}/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] brandLogo(@PathVariable UUID id) {
        return catalog.brandLogo(id);
    }
}
